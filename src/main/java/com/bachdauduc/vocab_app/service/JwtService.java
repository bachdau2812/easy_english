package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.UserInfo;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;


@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor

public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;

    @NonFinal
    @Value("${jwt.signerKey}")
    String SIGNED_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALID_DURATION;

    public String generateToken(UserInfo userInfo) {
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(userInfo.getId())
                .issuer("vtm.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "ROLE_" + userInfo.getUserRole())
                .build();

        return signClaims(jwtClaimsSet);
    }

    public String refreshToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier jwsVerifier = new MACVerifier(SIGNED_KEY.getBytes());

            if (!signedJWT.verify(jwsVerifier)) {
                throw new JwtException("Invalid signature");
            }

            if (redisTemplate.opsForValue().get(redisKeyProperties.logoutTokenKey(token)) != null) {
                throw new JwtException("Token logged out");
            }

            JWTClaimsSet oldClaims = signedJWT.getJWTClaimsSet();
            JWTClaimsSet refreshedClaims = new JWTClaimsSet.Builder(oldClaims)
                    .issueTime(new Date())
                    .expirationTime(new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            return signClaims(refreshedClaims);
        } catch (ParseException | JOSEException e) {
            throw new JwtException("Invalid token", e);
        }
    }

    private String signClaims(JWTClaimsSet jwtClaimsSet) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNED_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            logger.error("Cannot create token: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

//    public String buildScope(Employee employee){
//        StringJoiner stringJoiner = new StringJoiner(" ");
//        if (!CollectionUtils.isEmpty(employee.getRoles())) {
//            employee.getRoles().forEach(role -> {
//                stringJoiner.add("ROLE_" + role.getRoleName());
//                if (!CollectionUtils.isEmpty(role.getPermissions())) {
//                    role.getPermissions().forEach(permission -> {
//                        stringJoiner.add("PERMISSION_" + permission.getPermissionName());
//                    });
//                }
//            });
//        }
//
//        return stringJoiner.toString();
//    }

    public boolean verifyToken(String token) throws JOSEException, ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier jwsVerifier = new MACVerifier(SIGNED_KEY.getBytes());

        if (!signedJWT.verify(jwsVerifier)) {
            throw new JwtException("Invalid signature");
        }

        if (redisTemplate.opsForValue().get(redisKeyProperties.logoutTokenKey(token)) != null) {
            throw new JwtException("Token logged out");
        }

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        // Nên check thêm null để đề phòng token không có trường exp
        if (expiryTime == null || expiryTime.before(new Date())) {
            throw new JwtException("Token expired");
        }

        // Nếu vượt qua mọi chốt chặn, token hoàn toàn hợp lệ
        return true;
    }
}
