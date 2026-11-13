package mtuci.task2.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.math.BigDecimal;
import java.time.Instant;
import mtuci.task2.model.AccountStatus;
import mtuci.task2.model.PassStatus;
import mtuci.task2.model.Role;

/** Field binding distinguishes absent PATCH fields from explicit JSON null. */
public final class Requests {
    private Requests() {}

    public static final class UserRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private String name;
        @JsonSetter(nulls = Nulls.FAIL)
        private String email;
        @JsonSetter(nulls = Nulls.FAIL)
        private String password;
        @JsonSetter(nulls = Nulls.FAIL)
        private Role role;
        @JsonSetter(nulls = Nulls.FAIL)
        private AccountStatus status;

        @JsonCreator
        public UserRequest() {}

        public UserRequest(String name, String email, String password, Role role, AccountStatus status) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.role = role;
            this.status = status;
        }

        public String name() { return name; }
        public String email() { return email; }
        public String password() { return password; }
        public Role role() { return role; }
        public AccountStatus status() { return status; }
    }

    /** No role or status fields: an unknown field in the JSON is rejected, so nobody can register as staff. */
    public static final class RegisterRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private String name;
        @JsonSetter(nulls = Nulls.FAIL)
        private String email;
        @JsonSetter(nulls = Nulls.FAIL)
        private String password;
        @JsonSetter
        private String deviceId;

        @JsonCreator
        public RegisterRequest() {}

        public String name() { return name; }
        public String email() { return email; }
        public String password() { return password; }
        public String deviceId() { return deviceId; }
    }

    public static final class ChangePasswordRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private String currentPassword;
        @JsonSetter(nulls = Nulls.FAIL)
        private String newPassword;

        @JsonCreator
        public ChangePasswordRequest() {}

        public String currentPassword() { return currentPassword; }
        public String newPassword() { return newPassword; }
    }

    public static final class VehicleRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private String plateNumber;
        @JsonSetter(nulls = Nulls.FAIL)
        private Long ownerId;

        @JsonCreator
        public VehicleRequest() {}

        public VehicleRequest(String plateNumber, Long ownerId) {
            this.plateNumber = plateNumber;
            this.ownerId = ownerId;
        }

        public String plateNumber() { return plateNumber; }
        public Long ownerId() { return ownerId; }
    }

    public static final class ZoneRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private String name;
        @JsonSetter(nulls = Nulls.FAIL)
        private Integer capacity;
        @JsonSetter(nulls = Nulls.FAIL)
        private BigDecimal hourlyPrice;

        @JsonCreator
        public ZoneRequest() {}

        public ZoneRequest(String name, Integer capacity, BigDecimal hourlyPrice) {
            this.name = name;
            this.capacity = capacity;
            this.hourlyPrice = hourlyPrice;
        }

        public String name() { return name; }
        public Integer capacity() { return capacity; }
        public BigDecimal hourlyPrice() { return hourlyPrice; }
    }

    public static final class SessionCreate {
        @JsonSetter(nulls = Nulls.FAIL)
        private Long vehicleId;
        @JsonSetter(nulls = Nulls.FAIL)
        private Long zoneId;

        @JsonCreator
        public SessionCreate() {}

        public SessionCreate(Long vehicleId, Long zoneId) {
            this.vehicleId = vehicleId;
            this.zoneId = zoneId;
        }

        public Long vehicleId() { return vehicleId; }
        public Long zoneId() { return zoneId; }
    }

    public static final class SessionPatch {
        @JsonSetter(nulls = Nulls.FAIL)
        private Long vehicleId;
        @JsonSetter(nulls = Nulls.FAIL)
        private Long zoneId;
        @JsonSetter(nulls = Nulls.FAIL)
        private Boolean close;

        @JsonCreator
        public SessionPatch() {}

        public SessionPatch(Long vehicleId, Long zoneId, Boolean close) {
            this.vehicleId = vehicleId;
            this.zoneId = zoneId;
            this.close = close;
        }

        public Long vehicleId() { return vehicleId; }
        public Long zoneId() { return zoneId; }
        public Boolean close() { return close; }
    }

    public static final class PassRequest {
        @JsonSetter(nulls = Nulls.FAIL)
        private Long vehicleId;
        @JsonSetter(nulls = Nulls.FAIL)
        private Long zoneId;
        @JsonSetter(nulls = Nulls.FAIL)
        private Instant validFrom;
        @JsonSetter(nulls = Nulls.FAIL)
        private Instant validUntil;
        @JsonSetter(nulls = Nulls.FAIL)
        private PassStatus status;

        @JsonCreator
        public PassRequest() {}

        public PassRequest(Long vehicleId, Long zoneId, Instant validFrom, Instant validUntil, PassStatus status) {
            this.vehicleId = vehicleId;
            this.zoneId = zoneId;
            this.validFrom = validFrom;
            this.validUntil = validUntil;
            this.status = status;
        }

        public Long vehicleId() { return vehicleId; }
        public Long zoneId() { return zoneId; }
        public Instant validFrom() { return validFrom; }
        public Instant validUntil() { return validUntil; }
        public PassStatus status() { return status; }
    }
}
