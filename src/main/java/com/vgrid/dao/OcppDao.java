package com.vgrid.dao;

import com.vgrid.model.OcppChargerState;

import java.util.Map;

/** Live states reported by OCPP-connected chargers (written by the VGrid OCPP server). */
public interface OcppDao {

    /** "stationId/chargerId" -> what that charger last reported. Empty when no charger uses OCPP. */
    Map<String, OcppChargerState> findChargerStates();
}
