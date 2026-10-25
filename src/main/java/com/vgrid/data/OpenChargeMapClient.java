package com.vgrid.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vgrid.model.*;
import com.vgrid.util.Geo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads real charging stations from the Open Charge Map API (https://openchargemap.org)
 * and turns them into VGrid Station objects.
 *
 * Real from the API: name, address, operator, location, connector types, power and whether
 * each connector is operational. Open Charge Map has no live availability, ratings or wait times.
 * Station data © Open Charge Map contributors, licensed CC BY 4.0.
 */
public class OpenChargeMapClient {
    private static final String ENDPOINT = "https://api.openchargemap.io/v3/poi/";
    private static final int MAX_CHARGERS_PER_STATION = 8;
    private static final double DEFAULT_PRICE_PER_KWH = 20;
    private static final Pattern PRICE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:/|per)\\s*kwh", Pattern.CASE_INSENSITIVE);

    private final String apiKey;
    private final int maxResults;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenChargeMapClient(String apiKey, int maxResults) {
        this.apiKey = apiKey;
        this.maxResults = maxResults;
    }

    /** Stations within the city's radius. Throws IOException with a readable message on any problem. */
    public List<Station> fetchStations(City city) throws IOException, InterruptedException {
        String query = String.format(Locale.ROOT,
                "?output=json&countrycode=IN&latitude=%.4f&longitude=%.4f&distance=%d&distanceunit=KM"
                        + "&maxresults=%d&compact=false&verbose=false",
                city.latitude(), city.longitude(), city.radiusKm(), maxResults);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + query))
                .header("X-API-Key", apiKey)
                .header("User-Agent", "VGrid-EV-Charging/1.0 (student project)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("Open Charge Map could not be reached. Check your internet connection.", e);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IOException("Open Charge Map rejected the API key in db.properties (ocm.api.key).");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Open Charge Map answered with HTTP " + response.statusCode() + ".");
        }
        try {
            return parse(response.body(), city);
        } catch (RuntimeException e) {
            throw new IOException("Open Charge Map sent data VGrid could not read.", e);
        }
    }

    /** Converts the API's JSON (a list of "POIs") into stations. Public so it can be tested offline. */
    public static List<Station> parse(String json, City city) {
        List<Station> stations = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject poi = element.getAsJsonObject();
            Integer id = integer(poi, "ID");
            JsonObject address = object(poi, "AddressInfo");
            if (id == null || address == null || !seen.add(id)) continue;
            Double lat = number(address, "Latitude");
            Double lon = number(address, "Longitude");
            if (lat == null || lon == null) continue;

            boolean siteOperational = operational(object(poi, "StatusType"), true);
            List<Charger> chargers = chargers(array(poi, "Connections"), siteOperational);
            if (chargers.isEmpty()) continue; // no connector type VGrid supports (e.g. only 3-pin sockets)

            String name = limit(firstNonBlank(text(address, "Title"), "Charging station"), 100);
            String street = String.join(", ", nonBlank(text(address, "AddressLine1"), text(address, "AddressLine2")));
            String usageCost = text(poi, "UsageCost");
            String access = text(address, "AccessComments");

            stations.add(new Station("OCM" + id, city.id(), name,
                    limit(operator(poi), 100),
                    limit(firstNonBlank(street, name), 200),
                    limit(area(city, lat, lon, text(address, "Town")), 60),
                    lat, lon,
                    price(usageCost),
                    0,
                    access != null && access.contains("24") ? "Open 24 hours" : "Hours not listed",
                    amenities(poi, usageCost),
                    chargers,
                    "Open Charge Map #" + id));
        }
        return stations;
    }

    // ------------------------------------------------------------------ conversion helpers

    private static List<Charger> chargers(JsonArray connections, boolean siteOperational) {
        List<Charger> chargers = new ArrayList<>();
        if (connections == null) return chargers;
        for (JsonElement element : connections) {
            if (!element.isJsonObject()) continue;
            JsonObject c = element.getAsJsonObject();
            JsonObject type = object(c, "ConnectionType");
            Integer typeId = integer(c, "ConnectionTypeID");
            ConnectorType connector = connector(type == null ? null : text(type, "Title"), typeId == null ? -1 : typeId);
            if (connector == null) continue;

            Double power = number(c, "PowerKW");
            double kw = power != null && power > 0 ? Math.round(power * 10) / 10.0 : defaultPower(connector);
            Integer quantity = integer(c, "Quantity");
            int count = Math.max(1, Math.min(quantity == null ? 1 : quantity, 4));
            boolean working = siteOperational && operational(object(c, "StatusType"), true);
            for (int i = 0; i < count && chargers.size() < MAX_CHARGERS_PER_STATION; i++) {
                chargers.add(new Charger("C" + (chargers.size() + 1), connector, kw,
                        working ? ChargerStatus.AVAILABLE : ChargerStatus.OUT_OF_SERVICE));
            }
        }
        return chargers;
    }

    /** Open Charge Map connector names/ids -> the four standards VGrid supports. Others are skipped. */
    static ConnectorType connector(String title, int typeId) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (typeId == 33 || (t.contains("ccs") && t.contains("type 2"))) return ConnectorType.CCS2;
        if (typeId == 2 || t.contains("chademo")) return ConnectorType.CHADEMO;
        if (typeId == 25 || typeId == 1036 || t.startsWith("type 2")) return ConnectorType.TYPE_2;
        if (t.contains("gb-t") || t.contains("gb/t") || t.contains("bharat")) return ConnectorType.BHARAT_DC001;
        return null;
    }

    private static double defaultPower(ConnectorType type) {
        return switch (type) {
            case CCS2, CHADEMO -> 50;
            case TYPE_2 -> 22;
            case BHARAT_DC001 -> 15;
        };
    }

    private static String operator(JsonObject poi) {
        JsonObject info = object(poi, "OperatorInfo");
        String title = info == null ? null : text(info, "Title");
        if (title == null || title.toLowerCase(Locale.ROOT).contains("unknown")) {
            return "Independent operator";
        }
        return title;
    }

    /** The nearest of the city's areas (if within 4 km), otherwise the town from the API. */
    private static String area(City city, double lat, double lon, String town) {
        Location nearest = city.areas().stream()
                .min(Comparator.comparingDouble(a -> Geo.haversineKm(lat, lon, a.latitude(), a.longitude())))
                .orElseThrow();
        if (Geo.haversineKm(lat, lon, nearest.latitude(), nearest.longitude()) <= 4) {
            return nearest.name();
        }
        return firstNonBlank(town, city.name());
    }

    private static double price(String usageCost) {
        if (usageCost != null) {
            Matcher m = PRICE.matcher(usageCost);
            if (m.find()) {
                double value = Double.parseDouble(m.group(1));
                if (value > 0 && value < 100) return value;
            }
        }
        return DEFAULT_PRICE_PER_KWH;
    }

    private static List<String> amenities(JsonObject poi, String usageCost) {
        List<String> result = new ArrayList<>();
        JsonObject usage = object(poi, "UsageType");
        String usageTitle = usage == null ? null : text(usage, "Title");
        if (usageTitle != null) result.add(limit(usageTitle, 80));
        if (usageCost != null && !usageCost.isBlank()) result.add(limit("Cost: " + usageCost.trim(), 80));
        return result;
    }

    private static boolean operational(JsonObject status, boolean fallback) {
        if (status == null || !status.has("IsOperational") || status.get("IsOperational").isJsonNull()) {
            return fallback;
        }
        return status.get("IsOperational").getAsBoolean();
    }

    // ------------------------------------------------------------------ JSON helpers (null-safe)

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && !e.getAsString().isBlank() ? e.getAsString().trim() : null;
    }

    private static Double number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : null;
    }

    private static Integer integer(JsonObject o, String key) {
        Double d = number(o, key);
        return d == null ? null : d.intValue();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static List<String> nonBlank(String... values) {
        return Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList();
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
