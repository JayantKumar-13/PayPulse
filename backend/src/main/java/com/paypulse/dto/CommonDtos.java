package com.paypulse.dto;

public final class CommonDtos {

    private CommonDtos() {  //It is mainly a design/cleanliness practice to indicate This class is not meant to be instantiated.


    }

    //Prevents creating objects:

    public record MessageResponse(String msg) {
    }
}
// Why not Returning String return "Password changed successfully";
//Not JSON Inconsistent with other APIs Hard to extend later

//Returning DTO return new MessageResponse("Password changed successfully");Response:{ "msg": "Password changed successfully" }

//Benefits:Consistent JSON structure,Easy for frontend,Easy to add fields later
