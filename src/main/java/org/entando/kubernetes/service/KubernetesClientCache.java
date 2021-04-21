package org.entando.kubernetes.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class KubernetesClientCache extends ConcurrentHashMap<String, KubernetesClient> {

    final Timer timer = new Timer();
    final ConcurrentHashMap<String, Instant> creationTimes = new ConcurrentHashMap<>();
    private final int maximumAgeSeconds;
    private Function<String, KubernetesClient> kubernetesClientSupplier;

    //For tests
    public KubernetesClientCache(Function<String, KubernetesClient> kubernetesClientSupplier, int maximumAgeSeconds, long scanInterval) {
        //Remove after 1 hour
        //Scan every minute
        this(maximumAgeSeconds, scanInterval);
        this.kubernetesClientSupplier = kubernetesClientSupplier;
    }

    public KubernetesClientCache(Function<String, KubernetesClient> kubernetesClientSupplier) {
        //Remove after 1 hour
        //Scan every minute
        this(kubernetesClientSupplier, 3600, 60 * 100L);
    }

    private KubernetesClientCache(int maximumAgeSeconds, long scanInterval) {
        super();
        this.maximumAgeSeconds = maximumAgeSeconds;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                removeOldEntries();
            }

        }, 0, scanInterval);
    }

    @Override
    public KubernetesClient get(Object tokenAsObject) {
        final String token = (String) tokenAsObject;
        KubernetesClient kubernetesClient = super.get(token);
        if (kubernetesClient == null) {
            kubernetesClient = kubernetesClientSupplier.apply(token);
            creationTimes.put(token, Instant.now());
            super.put(token, kubernetesClient);
        }
        return kubernetesClient;
    }

    private void removeOldEntries() {
        Instant cutoffInstant = Instant.now().minusSeconds(maximumAgeSeconds);
        keySet().stream().filter(s -> creationTimes.get(s).isBefore(cutoffInstant)).forEach(token -> {
            //Synchronization risk here is minimal
            creationTimes.remove(token);
            remove(token).close();
        });
    }
}
