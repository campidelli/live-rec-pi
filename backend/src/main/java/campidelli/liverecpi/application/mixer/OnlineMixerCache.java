package campidelli.liverecpi.application.mixer;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import campidelli.liverecpi.domain.mixer.Mixer;

@Singleton
public class OnlineMixerCache {

    private final Map<String, Mixer> cache = new ConcurrentHashMap<>();

    public void update(List<Mixer> mixers) {
        cache.clear();
        for (Mixer mixer : mixers) {
            cache.put(mixer.id(), mixer);
        }
    }

    public List<Mixer> getAll() {
        return List.copyOf(cache.values());
    }

    public Optional<Mixer> getById(String id) {
        return Optional.ofNullable(cache.get(id));
    }
}