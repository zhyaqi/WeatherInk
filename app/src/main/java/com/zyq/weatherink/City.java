package com.zyq.weatherink;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public final class City {
    public final String id;
    public final String name;
    public final String adm1;
    public final String adm2;
    public final String lat;
    public final String lon;
    public final String utcOffset;

    public City(String id, String name, String adm1, String adm2,
                String lat, String lon, String utcOffset) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.adm1 = adm1 == null ? "" : adm1;
        this.adm2 = adm2 == null ? "" : adm2;
        this.lat = lat == null ? "" : lat;
        this.lon = lon == null ? "" : lon;
        this.utcOffset = utcOffset == null ? "" : utcOffset;
    }

    public static City fromJson(JSONObject object) {
        return new City(
                object.optString("id"),
                object.optString("name"),
                object.optString("adm1"),
                object.optString("adm2"),
                object.optString("lat"),
                object.optString("lon"),
                object.optString("utcOffset")
        );
    }

    public static City fromApi(JSONObject object) {
        return new City(
                object.optString("id"),
                object.optString("name"),
                object.optString("adm1"),
                object.optString("adm2"),
                object.optString("lat"),
                object.optString("lon"),
                object.optString("utcOffset")
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("adm1", adm1);
        object.put("adm2", adm2);
        object.put("lat", lat);
        object.put("lon", lon);
        object.put("utcOffset", utcOffset);
        return object;
    }

    public String regionText() {
        if (adm2.isEmpty() || adm2.equals(name)) {
            return adm1;
        }
        if (adm1.isEmpty()) {
            return adm2;
        }
        return adm1 + " · " + adm2;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof City && id.equals(((City) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
