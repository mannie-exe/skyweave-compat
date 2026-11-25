/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Keeps native-dispatch lambdas out of anonymous classes that would reference a Mixin class. */
public record RegionScanAdapter(Supplier<CompletableFuture<StructureSearchStorage.RegionPage>> reader,
        Closeable resource) implements StructureSearchStorage.RegionScan {
    @Override public CompletableFuture<StructureSearchStorage.RegionPage> next() { return reader.get(); }
    @Override public void close() throws IOException { resource.close(); }
    public static RegionScanAdapter unavailable() {
        return new RegionScanAdapter(() -> CompletableFuture.completedFuture(
                new StructureSearchStorage.RegionPage(java.util.List.of(), true, true)), () -> {});
    }
}
