package com.bookstore.member;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.common.ApiException;
import com.bookstore.member.MemberDtos.AddressRequest;
import com.bookstore.member.MemberDtos.AddressView;
import com.bookstore.member.MemberDtos.TopUpRequest;
import com.bookstore.member.MemberDtos.UserView;
import com.bookstore.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class MemberController {

    private final MemberService members;
    private final AddressRepository addresses;

    public MemberController(MemberService members, AddressRepository addresses) {
        this.members = members;
        this.addresses = addresses;
    }

    @GetMapping("/members/me")
    @Transactional(readOnly = true)
    public UserView me() {
        return UserView.of(members.get(CurrentUser.require().id()));
    }

    @PostMapping("/members/me/wallet/topup")
    public UserView topUp(@Valid @RequestBody TopUpRequest req) {
        return members.topUp(CurrentUser.require().id(), req.amount());
    }

    @GetMapping("/addresses")
    public List<AddressView> list() {
        return addresses.findByUserIdOrderByIdAsc(CurrentUser.require().id()).stream().map(AddressView::of).toList();
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AddressView create(@Valid @RequestBody AddressRequest req) {
        Address a = new Address();
        a.setUserId(CurrentUser.require().id());
        return save(a, req);
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public AddressView update(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return save(find(id), req);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        addresses.delete(find(id));
    }

    private Address find(Long id) {
        return addresses.findByIdAndUserId(id, CurrentUser.require().id())
                .orElseThrow(() -> ApiException.notFound("Address not found"));
    }

    private AddressView save(Address a, AddressRequest r) {
        a.setFirstName(r.firstName());
        a.setLastName(r.lastName());
        a.setAddressLine(r.addressLine());
        a.setEmail(r.email());
        a.setPhoneCountryCode(r.phoneCountryCode() == null ? "+91" : r.phoneCountryCode());
        a.setPhone(r.phone());
        a.setCity(r.city());
        a.setPin(r.pin());
        a.setState(r.state());
        a.setCountry(r.country());
        boolean makeDefault = Boolean.TRUE.equals(r.isDefault());
        if (makeDefault) {
            addresses.findByUserIdOrderByIdAsc(a.getUserId()).forEach(o -> o.setDefaultAddress(false));
        }
        a.setDefaultAddress(makeDefault);
        return AddressView.of(addresses.save(a));
    }
}
