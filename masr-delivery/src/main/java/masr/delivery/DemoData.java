package masr.delivery;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class DemoData {
    private DemoData() { }
    public static Platform create() {
        Platform platform = new Platform(Clock.system(ZoneId.of("Africa/Cairo")),DistanceTable.demo());
        platform.addRestaurant(new Restaurant("R1","Nile Kitchen","Maadi",List.of("Egyptian","Grill"),4.8,true));
        platform.addRestaurant(new Restaurant("R2","Pasta Corner","Dokki",List.of("Italian"),4.6,true));
        platform.addRestaurant(new Restaurant("R3","Green Bowl","Nasr City",List.of("Healthy","Vegetarian"),4.4,true));
        platform.addCustomer(new Customer("C1","Omar","01012345678",List.of(new Address("Faisal","12 El Malek St"),new Address("Maadi","5 Road 9")),new BigDecimal("5000")));
        platform.addCustomer(new Customer("C2","Salma","01123456789",List.of(new Address("Dokki","8 Tahrir St")),new BigDecimal("2500")));
        platform.addRider(new Rider("D1","Ahmed",VehiclePolicy.motorcycle(),"Maadi"));
        platform.addRider(new Rider("D2","Mona",VehiclePolicy.bicycle(),"Dokki"));
        platform.addRider(new Rider("D3","Karim",VehiclePolicy.car(),"Nasr City"));
        LocalDate expiry = LocalDate.now(platform.clock()).plusYears(1);
        platform.addPromotion(new Promotion("NILE20",Promotion.percentage(new BigDecimal("0.20"),new BigDecimal("50")),List.of(Promotion.expires(expiry))));
        platform.addPromotion(new Promotion("WELCOME30",Promotion.fixed(new BigDecimal("30")),List.of(Promotion.firstTime(),Promotion.minimum(new BigDecimal("100")),Promotion.expires(expiry))));
        platform.addPromotion(new Promotion("FREEDEL",Promotion.freeDelivery(),List.of(Promotion.minimum(new BigDecimal("150")),Promotion.expires(expiry))));
        loadMenu(platform);
        return platform;
    }
    private static void loadMenu(Platform platform) {
        MenuItemFactory factory = new MenuItemFactory();
        try (InputStream stream = DemoData.class.getResourceAsStream(AppConfig.get().menuResource())) {
            if (stream == null) throw new IllegalStateException("Missing menu data.");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8))) {
                String line; int number = 0;
                while ((line = reader.readLine()) != null) {
                    number++; if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\|",-1);
                    if (fields.length != 9) throw new IllegalStateException("Invalid menu row " + number);
                    Restaurant restaurant = platform.restaurant(fields[0]);
                    MenuItem item = factory.create(fields[1],new MenuItemFactory.Spec(restaurant,fields[2],fields[3],fields[4],Integer.parseInt(fields[5]),fields[6],fields[7]));
                    platform.addItem(restaurant.id(),item,new BigDecimal(fields[8]));
                }
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load menu data",e); }
    }
}
