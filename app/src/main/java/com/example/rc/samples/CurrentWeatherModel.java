package com.example.rc.samples;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CurrentWeatherModel {

    private String desc;

    private Integer clouds;

    private Integer temperature;

    private String placeName;

    public CurrentWeatherModel(String desc, Integer clouds, Integer temperature, String placeName) {
        this.desc = desc;
        this.clouds = clouds;
        this.temperature = temperature;
        this.placeName = placeName;
    }

    public static CurrentWeatherModel serialize(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String name = root.getString("name");
        Integer temperature = root.getJSONObject("main").getInt("temp");
        Integer clouds = root.getJSONObject("clouds").getInt("all");
        JSONArray weatherArray = root.getJSONArray("weather");

        String desc = "";
        if (weatherArray.length() > 0) {
            desc = ((JSONObject) weatherArray.get(0)).getString("description");
        }
        return new CurrentWeatherModel(desc, clouds, temperature, name);
    }

    public String getDesc() {
        return desc;
    }

    public Integer getClouds() {
        return clouds;
    }

    public Integer getTemperature() {
        return temperature;
    }

    public String getPlaceName() {
        return placeName;
    }

}
