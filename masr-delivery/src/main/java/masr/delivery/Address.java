package masr.delivery;

public record Address(String district, String details) {
    public Address {
        district = Money.text(district, "District");
        details = Money.text(details, "Address details");
    }
}
