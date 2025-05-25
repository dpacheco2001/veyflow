package com.veyon.veyflow;

import com.google.gson.JsonObject;
import com.veyon.veyflow.state.AgentState;
import com.veyon.veyflow.tools.ToolAnnotation;
import com.veyon.veyflow.tools.ToolParameter;
import com.veyon.veyflow.tools.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;


public class WeatherToolService extends ToolService {
    private static final Logger log = LoggerFactory.getLogger(WeatherToolService.class);
    private final Random random = new Random();

    @ToolAnnotation("Get the current weather conditions for a specified location")
    public JsonObject getWeather(
            @ToolParameter(value = "The city or location to get weather for", type = "string", required = true) String location,
            AgentState state) {
        if (location == null || location.isEmpty()) {
            location = "default location";
        }
            
        log.info("Getting weather for location: {}", location);
        
        String weather = random.nextBoolean() ? "sunny" : "rainy";
        int temperature = 15 + random.nextInt(20); 
        
        JsonObject result = new JsonObject();
        result.addProperty("location", location);
        result.addProperty("condition", weather);
        result.addProperty("temperature", temperature);
        result.addProperty("unit", "celsius");
        
        return result;
    }
    

    @ToolAnnotation("Get the weather forecast for a specified location")
    public JsonObject getForecast(
            @ToolParameter(value = "The city or location to get forecast for", type = "string", required = true) String location,
            @ToolParameter(value = "Number of days to forecast", type = "integer", required = true) int days,
            AgentState state) {
        if (location == null || location.isEmpty()) {
            location = "default location";
        }
        if (days <= 0) {
            days = 3;
        }
            
        log.info("Getting forecast for location: {} for {} days", location, days);
        
        JsonObject result = new JsonObject();
        result.addProperty("location", location);
        
        JsonObject forecast = new JsonObject();
        String[] conditions = {"sunny", "rainy", "cloudy", "partly cloudy", "stormy"};
        
        for (int i = 0; i < days; i++) {
            JsonObject dayForecast = new JsonObject();
            String condition = conditions[random.nextInt(conditions.length)];
            int temperature = 15 + random.nextInt(20);
            
            dayForecast.addProperty("condition", condition);
            dayForecast.addProperty("temperature", temperature);
            dayForecast.addProperty("unit", "celsius");
            
            forecast.add("day" + (i+1), dayForecast);
        }
        
        result.add("forecast", forecast);
        return result;
    }
    

}
