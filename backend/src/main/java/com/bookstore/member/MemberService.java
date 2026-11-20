package com.bookstore.member;

import java.math.BigDecimal;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.common.ApiException;
import com.bookstore.member.MemberDtos.AuthResponse;
import com.bookstore.member.MemberDtos.LoginRequest;
import com.bookstore.member.MemberDtos.RegisterRequest;
import com.bookstore.member.MemberDtos.UserView;
import com.bookstore.order.BasketService;
import com.bookstore.security.JwtService;

@Service
public class MemberService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final BasketService baskets;

    public MemberService(UserRepository users, PasswordEncoder encoder, JwtService jwt, BasketService baskets) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.baskets = baskets;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email is already registered");
        }
        User u = new User();
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.CUSTOMER);
        users.save(u);
        return response(u);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String guestToken) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(found -> encoder.matches(req.password(), found.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        baskets.mergeGuestBasket(u.getId(), guestToken);
        return response(u);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional
    public UserView topUp(Long id, BigDecimal amount) {
        User u = get(id);
        u.setWalletBalance(u.getWalletBalance().add(amount));
        return UserView.of(u);
    }

    private AuthResponse response(User u) {
        return new AuthResponse(jwt.generate(u), "Bearer", UserView.of(u));
    }
}
