package com.vgrid.model;

import java.util.List;

/**
 * A city users can choose in Settings. Stations are downloaded around its centre (within radiusKm),
 * and its areas are offered as starting locations.
 */
public record City(String id, String name, double latitude, double longitude, int radiusKm, List<Location> areas) {

    public static final City PUNE = new City("pune", "Pune", 18.5204, 73.8567, 25, List.of(
            new Location("Baner", 18.5590, 73.7868),
            new Location("Hinjewadi", 18.5913, 73.7389),
            new Location("Kothrud", 18.5074, 73.8077),
            new Location("Shivajinagar", 18.5308, 73.8475),
            new Location("Koregaon Park", 18.5362, 73.8940),
            new Location("Viman Nagar", 18.5679, 73.9143),
            new Location("Hadapsar", 18.5089, 73.9260)));

    public static final City MUMBAI = new City("mumbai", "Mumbai", 19.0760, 72.8777, 30, List.of(
            new Location("Andheri", 19.1136, 72.8697),
            new Location("Bandra", 19.0596, 72.8295),
            new Location("Powai", 19.1176, 72.9060),
            new Location("Lower Parel", 18.9986, 72.8296),
            new Location("Colaba", 18.9067, 72.8147),
            new Location("Thane", 19.2183, 72.9781),
            new Location("Vashi (Navi Mumbai)", 19.0771, 72.9986)));

    public static final City DELHI = new City("delhi", "Delhi NCR", 28.6139, 77.2090, 30, List.of(
            new Location("Connaught Place", 28.6315, 77.2167),
            new Location("Karol Bagh", 28.6519, 77.1909),
            new Location("Saket", 28.5245, 77.2066),
            new Location("Dwarka", 28.5921, 77.0460),
            new Location("Noida Sector 18", 28.5708, 77.3260),
            new Location("Gurugram Cyber City", 28.4950, 77.0895)));

    public static final City BENGALURU = new City("bengaluru", "Bengaluru", 12.9716, 77.5946, 25, List.of(
            new Location("MG Road", 12.9756, 77.6066),
            new Location("Indiranagar", 12.9784, 77.6408),
            new Location("Koramangala", 12.9352, 77.6245),
            new Location("Whitefield", 12.9698, 77.7500),
            new Location("Electronic City", 12.8452, 77.6602),
            new Location("Hebbal", 13.0354, 77.5970)));

    public static final City HYDERABAD = new City("hyderabad", "Hyderabad", 17.3850, 78.4867, 25, List.of(
            new Location("HITEC City", 17.4435, 78.3772),
            new Location("Gachibowli", 17.4401, 78.3489),
            new Location("Banjara Hills", 17.4126, 78.4482),
            new Location("Secunderabad", 17.4399, 78.4983),
            new Location("Kukatpally", 17.4849, 78.4138),
            new Location("Charminar", 17.3616, 78.4747)));

    public static final City CHENNAI = new City("chennai", "Chennai", 13.0827, 80.2707, 25, List.of(
            new Location("T. Nagar", 13.0418, 80.2341),
            new Location("Anna Nagar", 13.0850, 80.2101),
            new Location("Adyar", 13.0012, 80.2565),
            new Location("Guindy", 13.0067, 80.2206),
            new Location("Velachery", 12.9815, 80.2180),
            new Location("Sholinganallur (OMR)", 12.9010, 80.2279)));

    public static final List<City> ALL = List.of(PUNE, MUMBAI, DELHI, BENGALURU, HYDERABAD, CHENNAI);

    /** The city with this id, or Pune if the id is unknown. */
    public static City byId(String id) {
        return ALL.stream().filter(c -> c.id.equals(id)).findFirst().orElse(PUNE);
    }

    /** The area with this name, or the first area. */
    public Location area(String name) {
        return areas.stream().filter(a -> a.name().equals(name)).findFirst().orElse(areas.get(0));
    }

    @Override
    public String toString() {
        return name;
    }
}
