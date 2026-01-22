package com.ird0.portal.dto.response;

import java.util.UUID;

public record ActorDTO(
    UUID id, String name, String type, String email, String phone, String address) {}
