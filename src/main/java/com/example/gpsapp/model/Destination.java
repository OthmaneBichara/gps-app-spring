package com.example.gpsapp.model;
import lombok.Data;

@Data
public class Destination {
    private String address;
    private String estimatedTime;
    private String distance;
    private String startCoords;
    private String endCoords;
    private String routeGeometry;
}