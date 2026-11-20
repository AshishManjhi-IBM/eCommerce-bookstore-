package com.bookstore.member;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record RegisterRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserView(Long id, String firstName, String lastName, String email, Role role, int giftPoints,
            BigDecimal walletBalance) {

        public static UserView of(User u) {
            return new UserView(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getRole(),
                    u.getGiftPoints(), u.getWalletBalance());
        }
    }

    public record AuthResponse(String token, String tokenType, UserView user) {
    }

    public record TopUpRequest(@NotNull @DecimalMin("1") BigDecimal amount) {
    }

    public record AddressRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String addressLine,
            @NotBlank @Email String email,
            String phoneCountryCode,
            @NotBlank String phone,
            @NotBlank String city,
            @NotBlank String pin,
            @NotBlank String state,
            @NotBlank String country,
            Boolean isDefault) {
    }

    public record AddressView(Long id, String firstName, String lastName, String addressLine, String email,
            String phoneCountryCode, String phone, String city, String pin, String state, String country,
            boolean isDefault) {

        public static AddressView of(Address a) {
            return new AddressView(a.getId(), a.getFirstName(), a.getLastName(), a.getAddressLine(), a.getEmail(),
                    a.getPhoneCountryCode(), a.getPhone(), a.getCity(), a.getPin(), a.getState(), a.getCountry(),
                    a.isDefaultAddress());
        }
    }
}
