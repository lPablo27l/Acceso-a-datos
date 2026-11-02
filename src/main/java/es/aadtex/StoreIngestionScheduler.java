package es.aadtex;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoreIngestionScheduler {
    private final StoreIngestionService ingestion;
    private volatile boolean ready;

    public StoreIngestionScheduler(StoreIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        ready = true;
    }

    @Scheduled(fixedRate = 5000)
    public void processStoreBatches() {
        if (ready) ingestion.processPending();
    }
}
