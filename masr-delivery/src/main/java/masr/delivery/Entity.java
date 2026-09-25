package masr.delivery;

public abstract class Entity {
    private final String id;
    protected Entity(String id) { this.id = Money.text(id, "Identifier"); }
    public final String id() { return id; }
    @Override public final boolean equals(Object other) {
        return other != null && getClass() == other.getClass() && id.equals(((Entity) other).id);
    }
    @Override public final int hashCode() { return 31 * getClass().hashCode() + id.hashCode(); }
}
