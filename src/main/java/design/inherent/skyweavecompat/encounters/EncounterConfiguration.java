package design.inherent.skyweavecompat.encounters;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class EncounterConfiguration {
    public static final String FILE="skyweave_compat-encounters.json";
    private static final com.google.gson.Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static volatile EncounterProfile active=EncounterProfile.DEFAULT;
    public static EncounterProfile profile(){return active;}
    public static EncounterProfile parse(String text){return ProfileJson.record(text,EncounterProfile.class);}
    public static String normalize(String text){return JSON.toJson(parse(text))+"\n";}
    public static void load(Path directory){
        var path=directory.resolve(FILE);
        try{
            if(!Files.exists(path))Files.writeString(path,JSON.toJson(EncounterProfile.DEFAULT)+"\n",StandardOpenOption.CREATE_NEW);
            active=parse(ConfigFile.read(path));
        }catch(IOException|RuntimeException error){throw new IllegalStateException("Cannot load "+path+": "+error.getMessage(),error);}
    }
    private EncounterConfiguration(){}
}
