package com.irctc.rrs.inventory.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irctc.rrs.common.time.TimeProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seeds real Indian Railways trains extracted from datameet/railways
 * across current and upcoming journey dates.
 */
@Component
public class TrainDataLoader {

    private static final Logger log = LoggerFactory.getLogger(TrainDataLoader.class);

    private final ObjectMapper objectMapper;
    private final TimeProvider timeProvider;
    private final InventoryProperties properties;

    private final Map<String, Train> trainCatalog = new LinkedHashMap<>();
    private final Map<String, JourneyInstance> journeysByKey = new ConcurrentHashMap<>();

    public TrainDataLoader(
            ObjectMapper objectMapper,
            TimeProvider timeProvider,
            InventoryProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
        this.properties = properties;
    }

    @PostConstruct
    public void loadInitialCatalog() {
        ClassPathResource resource = new ClassPathResource("data/indian-railways-trains.json");
        try (InputStream is = resource.getInputStream()) {
            List<Train> trains = objectMapper.readValue(is, new TypeReference<>() {});
            LocalDate today = timeProvider.currentDate();
            for (Train train : trains) {
                trainCatalog.put(train.number(), train);
                for (int offset = 0; offset <= 2; offset++) {
                    LocalDate date = today.plusDays(offset);
                    journeysByKey.put(journeyKey(train.number(), date), new JourneyInstance(
                            train,
                            date,
                            properties.getCoachesPerClass(),
                            properties.getBerthsPerCoach(),
                            properties.getRacSideLowerSlotsPerCoach(),
                            properties.getMaxWaitlistLimit()
                    ));
                }
            }
            log.info("Loaded {} Indian Railways trains into Inventory Context ({} journey instances)",
                    trainCatalog.size(), journeysByKey.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Indian Railways seed dataset", e);
        }
    }

    public Collection<Train> getAllTrains() {
        return trainCatalog.values();
    }

    public Train getTrain(String trainNumber) {
        return trainCatalog.get(trainNumber);
    }

    public JourneyInstance getJourney(String trainNumber, LocalDate journeyDate) {
        return journeysByKey.get(journeyKey(trainNumber, journeyDate));
    }

    public JourneyInstance getOrCreateJourney(
            String trainNumber,
            LocalDate journeyDate,
            int coachesPerClass,
            int berthsPerCoach,
            int racSlotsPerCoach,
            int maxWlLimit
    ) {
        Train train = trainCatalog.get(trainNumber);
        if (train == null) {
            return null;
        }
        return journeysByKey.computeIfAbsent(journeyKey(trainNumber, journeyDate),
                ignored -> new JourneyInstance(train, journeyDate, coachesPerClass, berthsPerCoach, racSlotsPerCoach, maxWlLimit));
    }

    public void putSimulationJourney(String trainNumber, LocalDate journeyDate, JourneyInstance instance) {
        journeysByKey.put(journeyKey(trainNumber, journeyDate), instance);
    }

    private String journeyKey(String trainNumber, LocalDate journeyDate) {
        return trainNumber + ":" + journeyDate;
    }
}
