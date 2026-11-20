package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;

/** Native server policies and local host forms share these exact typed identities. */
public final class PolicyDefinitions {
    public static final DocumentDefinition<WayfinderSettings> WAYFINDER =
            ReformDocuments.record(WayfinderSettings.FILE, WayfinderSettings.class, WayfinderSettings.DEFAULT);
    public static final DocumentDefinition<SearchSettings> SEARCH =
            ReformDocuments.record(SearchSettings.FILE, SearchSettings.class, SearchSettings.DEFAULT);
    private PolicyDefinitions() {}
}
