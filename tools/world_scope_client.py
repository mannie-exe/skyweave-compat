#!/usr/bin/env python3
"""Qualify remote climate admission/reset in a disposable packaged client (desktop access required)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time

from client_bootstrap import REPOSITORY, argument_file, packaged_bootstrap


def stop(process):
    if process is None or process.poll() is not None:
        return
    try:
        process.communicate('stop\n', timeout=30)
    except (subprocess.TimeoutExpired, BrokenPipeError):
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait()


def cached(group, name, version):
    files = [p for p in (Path.home() / '.gradle/caches/modules-2/files-2.1' / group / name / version).glob('*/*.jar')
             if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if len(files) != 1:
        raise ValueError(f'Expected one exact cached artifact: {group}:{name}:{version}')
    return files[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path, required=True, help='Read-only game root supplying libraries/EULA')
    parser.add_argument('--java', required=True)
    parser.add_argument('--output', type=Path, required=True, help='New owned evidence directory')
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists(): parser.error('Evidence directory must be new')
    output.mkdir(parents=True)
    properties = dict(line.split('=',1) for line in (REPOSITORY/'gradle.properties').read_text().splitlines() if '=' in line)
    product = REPOSITORY / f"build/libs/skyweave-compat-{properties['mod_version']}-mc1.21.1-neoforge.jar"
    probe = REPOSITORY/'build/probe/skyweave-client-probe.jar'
    mods = [product,cached('maven.modrinth','sky-archipelago','XPc17YJD'),cached('design.inherent','reform',properties['reform_version'])]
    report = dict(complete=False, phases=[], artifacts={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [*mods,probe]})
    server_process = client = None
    sequence = 0
    started = time.monotonic()
    with tempfile.TemporaryDirectory(prefix='skyweave-climate-scope-') as temporary:
        root = Path(temporary)
        game = root/'client'; (game/'mods').mkdir(parents=True)
        for p in [*mods,probe]: shutil.copy2(p,game/'mods'/p.name)
        (game/'misc-review-fixture.json').write_text('{"owner":"world_scope_client.py"}\n')
        (game/'options.txt').write_text('pauseOnLostFocus:false\nfullscreen:false\nrenderDistance:2\nsimulationDistance:2\nmaxFps:30\n')
        def start_server(name):
            run=root/name;(run/'mods').mkdir(parents=True)
            for p in mods:shutil.copy2(p,run/'mods'/p.name)
            (run/'libraries').symlink_to(args.server.resolve()/'libraries',target_is_directory=True)
            shutil.copy2(args.server/'eula.txt',run/'eula.txt')
            if name=='off':
                p=run/'config/skyweave/skyweave_compat-features.properties';p.parent.mkdir(parents=True)
                p.write_text('terrain.climate=false\n')
            with socket.socket() as listener:
                listener.bind(('127.0.0.1',0));port=listener.getsockname()[1]
            (run/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=world\n'
                'level-type=skyweave_compat:islands_v1\nlevel-seed=42\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n'
                'max-tick-time=60000\ndifficulty=peaceful\ngamemode=creative\n')
            launch=args.server.resolve()/f"libraries/net/neoforged/neoforge/{properties['neo_version']}/unix_args.txt"
            command=[args.java,'-Xmx2G','-XX:ActiveProcessorCount=4','@'+str(launch),'nogui']
            with (output/f'server-{name}.log').open('w') as log:
                process=subprocess.Popen(command,cwd=run,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
            deadline=time.monotonic()+180
            while process.poll() is None and time.monotonic()<deadline:
                if 'Done (' in (output/f'server-{name}.log').read_text(errors='replace'):
                    report[name+'Server']=dict(command=command,port=port);return process,port
                time.sleep(.5)
            stop(process);raise RuntimeError(f'{name} server did not start; retained log')
        def control(operation,**values):
            nonlocal sequence
            sequence+=1;p=game/'misc-review-control.json';temp=game/'control.tmp'
            temp.write_text(json.dumps(dict(sequence=sequence,operation=operation,**values)));temp.replace(p)
        def wait(label,predicate,timeout=90):
            deadline=time.monotonic()+timeout;last={}
            while client.poll() is None and time.monotonic()<deadline:
                try:last=json.loads((game/'misc-review-state.json').read_text())
                except (FileNotFoundError,json.JSONDecodeError):pass
                if last.get('error'):raise RuntimeError(last['error'])
                if predicate(last):
                    report['phases'].append(dict(name=label,state=last));print(label,flush=True);return last
                time.sleep(.5)
            raise RuntimeError(f'{label} did not complete: {last}')
        def admitted(s):return s.get('loaded') and s.get('climate',{}).get('synchronizedPolicy')
        try:
            server_process,port=start_server('on')
            vm,program=packaged_bootstrap(game)
            vm += ['-Xms512M','-Xmx3G','-XX:ActiveProcessorCount=4','-Dskyweave_compat.probe.misc=true',
                   '-Dskyweave_compat.probe.worldScope=true',f'-Dskyweave_compat.probe.misc.server=127.0.0.1:{port}']
            if '--username' in program:program[program.index('--username')+1]='ScopeClimate'
            else:program+=['--username','ScopeClimate']
            argument_file(game/'vm.args',vm);argument_file(game/'program.args',program)
            command=[args.java,'-XstartOnFirstThread','@'+str(game/'classpath.args'),'@'+str(game/'vm.args'),
                     'net.neoforged.devlaunch.Main','@'+str(game/'program.args')]
            report['clientCommand']=command
            environment=dict(os.environ);environment.pop('MOD_CLASSES',None)
            with (output/'client.log').open('w') as log:
                client=subprocess.Popen(command,cwd=game,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True,env=environment)
            first=wait('owned-policy',lambda s:admitted(s) and s.get('climate',{}).get('layers',0)>0,150)
            assert first['climateChannel'] and first['climateApplies'] and first['missingTemperatureY200']==64
            assert first['temperatureY200']==(first.get('columnReferenceY200') if first.get('columnReferenceY200') is not None else 64)
            origin=first['position']
            server_process.stdin.write('execute in minecraft:the_end run tp ScopeClimate 0 80 0\n');server_process.stdin.flush()
            end=wait('foreign-dimension',lambda s:s.get('loaded') and s.get('dimension')=='minecraft:the_end')
            assert not end['climate']['synchronizedPolicy'] and not end['climateApplies'] and end['temperatureY200']==200
            assert end['climateTracking']['chunks']==0
            server_process.stdin.write(f"execute in minecraft:overworld run tp ScopeClimate {origin['x']} {origin['y']} {origin['z']}\n");server_process.stdin.flush()
            wait('owned-return',lambda s:admitted(s) and s.get('dimension')=='minecraft:overworld')
            connection=first['connectionIdentity'];control('reconnect')
            wait('same-process-reconnect',lambda s:admitted(s) and s.get('connectionIdentity')!=connection)
            control('disconnect');cleared=wait('disconnect-clears',lambda s:not s.get('loaded') and s.get('sequence')==sequence)
            assert not cleared['climateTracking']['synchronizedPolicy'] and cleared['climateTracking']['chunks']==0
            stop(server_process);server_process=None
            server_process,port=start_server('off');control('connect',address=f'127.0.0.1:{port}')
            off=wait('remote-climate-off',lambda s:s.get('loaded') and s.get('sequence')==sequence)
            assert off['climateApplies'] and not off['climateChannel'] and not off['climate']['synchronizedPolicy']
            assert off['temperatureY200']==200 and off['missingTemperatureY200']==200
            control('disconnect');wait('off-disconnect-clears',lambda s:not s.get('loaded') and s.get('sequence')==sequence)
            control('shutdown');client.wait(timeout=30)
            assert client.returncode==0
            report['complete']=True
        except BaseException as error:
            report['error']=repr(error);raise
        finally:
            stop(client);stop(server_process)
            report['seconds']=round(time.monotonic()-started,2)
            (output/'result.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':main()
