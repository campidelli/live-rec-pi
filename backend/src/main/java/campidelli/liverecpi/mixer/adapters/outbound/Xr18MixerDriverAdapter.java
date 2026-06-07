package campidelli.liverecpi.mixer.adapters.outbound;

import java.util.List;

import com.illposed.osc.OSCMessage;

import campidelli.liverecpi.mixer.adapters.outbound.network.OscMessageSerializer;
import campidelli.liverecpi.mixer.adapters.outbound.network.UdpOscMixerScanner;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.AvailableMixer;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer.UnavailableMixer;
import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;
import campidelli.liverecpi.mixer.ports.outbound.MixerDiscoveryPort;
import campidelli.liverecpi.mixer.ports.outbound.MixerRegistryPort;
import jakarta.inject.Singleton;

@Singleton
public class Xr18MixerDriverAdapter implements MixerRegistryPort, MixerDiscoveryPort {

  private final UdpOscMixerScanner scanner;
  private final OscMessageSerializer serializer;

  public Xr18MixerDriverAdapter(UdpOscMixerScanner scanner, OscMessageSerializer serializer) {
    this.scanner = scanner;
    this.serializer = serializer;
  }

  @Override
  public MixerDescriptor getDescriptor() {
      return new MixerDescriptor("XR18", "Behringer", "X Air XR18", 18);
  }

  @Override
  public List<DiscoveredMixer> discover() {
    MixerDescriptor descriptor = getDescriptor();
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
                extractNameFromSignature(availableMixer.signatureParts()), // update the name based on the signature
                descriptor.numberOfChannels()),
            availableMixer.signatureParts(),
            availableMixer.connection()))
        .toList();
  }

  private String extractNameFromSignature(List<String> signatureParts) {
    // For XR18, we expect the signature to contain the name as the second part of the signature
    return signatureParts.get(1);
  }
}
