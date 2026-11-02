package es.aadtex;

import java.io.IOException;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoreIngestionScheduler {
    private static final Logger log=LoggerFactory.getLogger(StoreIngestionScheduler.class);
    private final StoreIngestionService ingestion;
    private final boolean enabled;
    private volatile boolean ready;
    public StoreIngestionScheduler(StoreIngestionService ingestion,
        @Value("${aadtex.scheduler-enabled:true}") boolean enabled) {
        this.ingestion=ingestion; this.enabled=enabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { ready=true; }
    @Scheduled(fixedRate = 5000)
    public void processStoreBatches() {
        if (!ready || !enabled) return;
        try { ingestion.processPending(); }
        catch (IOException e) { log.error("Error de acceso a los directorios de trabajo",e); }
    }
}
