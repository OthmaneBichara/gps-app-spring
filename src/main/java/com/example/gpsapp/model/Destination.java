package com.example.gpsapp.model;
import lombok.Data;

@Data // Lombok génère les getters/setters
public class Destination {
    private String address;
    private String estimatedTime;
    private String distance;
    private String startCoords;
    private String endCoords;
    private String routeGeometry;
}