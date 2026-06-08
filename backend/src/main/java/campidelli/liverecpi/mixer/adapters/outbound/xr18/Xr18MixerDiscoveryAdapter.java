package campidelli.liverecpi.mixer.adapters.outbound.xr18;

import java.util.List;

import com.illposed.osc.OSCMessage;

import campidelli.liverecpi.mixer.adapters.outbound.network.OscMessageSerializer;
import campidelli.liverecpi.mixer.adapters.outbound.network.UdpOscMixerScanner;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.AvailableMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.UnavailableMixer;
import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;
import campidelli.liverecpi.mixer.ports.outbound.GetMixerDescriptorPort;
import campidelli.liverecpi.mixer.ports.outbound.DiscoverMixerPort;
import jakarta.inject.Singleton;

@Singleton
public class Xr18MixerDiscoveryAdapter implements DiscoverMixerPort {

    private final UdpOscMixerScanner scanner;
    private final OscMessageSerializer serializer;
    private final GetMixerDescriptorPort descriptorPort;

    public Xr18MixerDiscoveryAdapter(
            UdpOscMixerScanner scanner,
            OscMessageSerializer serializer,
            GetMixerDescriptorPort descriptorPort) {
        this.scanner = scanner;
        this.serializer = serializer;
        this.descriptorPort = descriptorPort;
    }

    @Override
    public List<DiscoveredMixer> discover() {
        MixerDescriptor descriptor = descriptorPort.getDescriptor();
        byte[] payload = serializer.serialize(new OSCMessage("/xinfo"));

        List<AvailableMixer> availableMixers = scanner.discover(descriptor, payload, 10024);
        if (availableMixers.isEmpty()) {
            return List.of(new UnavailableMixer(descriptor));
        }

        return availableMixers.stream()
                .map(availableMixer -> (DiscoveredMixer) new AvailableMixer(
                        new MixerDescriptor(
                                descriptor.type(),
                                descriptor.vendor(),
                                extractNameFromSignature(availableMixer.signatureParts()),
                                descriptor.numberOfChannels()),
                        availableMixer.signatureParts(),
                        availableMixer.connection()))
                .toList();
    }

    private String extractNameFromSignature(List<String> signatureParts) {
        return signatureParts.get(1);
    }
}
