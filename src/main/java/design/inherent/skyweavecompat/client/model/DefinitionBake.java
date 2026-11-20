package design.inherent.skyweavecompat.client.model;

/** Binds a prepared definition set to the exact native bakery which collected its dependencies. */
public interface DefinitionBake {
    ItemDefinitions.Prepared skyweave$definitions();
}
