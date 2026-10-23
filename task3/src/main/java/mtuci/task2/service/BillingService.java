package mtuci.task2.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import mtuci.task2.model.ParkingPass;
import mtuci.task2.model.ParkingSession;
import mtuci.task2.model.PassStatus;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
    private record Interval(Instant start, Instant end) {}


    public BigDecimal calculate(ParkingSession session, Instant exitTime, List<ParkingPass> passes) {
        List<Interval> covered = passes.stream()
                .filter(pass -> pass.status() == PassStatus.ACTIVE)
                .filter(pass -> pass.vehicleId().equals(session.vehicleId()) && pass.zoneId().equals(session.zoneId()))
                .map(pass -> new Interval(max(pass.validFrom(), session.entryTime()), min(pass.validUntil(), exitTime)))
                .filter(interval -> interval.start().isBefore(interval.end()))
                .sorted(Comparator.comparing(Interval::start))
                .toList();

        BigDecimal coveredSeconds = BigDecimal.ZERO;
        Instant start = null;
        Instant end = null;
        for (Interval interval : covered) {
            if (start == null) {
                start = interval.start();
                end = interval.end();
            } else if (!interval.start().isAfter(end)) {
                end = max(end, interval.end());
            } else {
                coveredSeconds = coveredSeconds.add(seconds(start, end));
                start = interval.start();
                end = interval.end();
            }
        }
        if (start != null) {
            coveredSeconds = coveredSeconds.add(seconds(start, end));
        }
        BigDecimal paidSeconds = seconds(session.entryTime(), exitTime).subtract(coveredSeconds);
        return session.hourlyPrice().multiply(paidSeconds)
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal seconds(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
    }

    private Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
