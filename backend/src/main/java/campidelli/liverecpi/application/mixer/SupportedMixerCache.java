package campidelli.liverecpi.application.mixer;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SupportedMixerCache {

    private final Map<String, SupportedMixerDTO> cache = new ConcurrentHashMap<>();

    public void update(List<SupportedMixerDTO> mixers) {
        cache.clear();
        mixers.forEach(mixer -> cache.put(mixer.id(), mixer));
    }

    public List<SupportedMixerDTO> getAll() {
        return List.copyOf(cache.values());
    }

    public Optional<SupportedMixerDTO> getById(String id) {
        return Optional.ofNullable(cache.get(id));
    }
}