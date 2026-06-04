package campidelli.liverecpi.infrastructure.network;

import campidelli.liverecpi.application.mixer.DeviceDiscoveryPort;
import campidelli.liverecpi.domain.mixer.OnlineDevice;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.*;

@Singleton
public class UdpDeviceDiscoveryAdapter implements DeviceDiscoveryPort {

  private static final Logger log = LoggerFactory.getLogger(UdpDeviceDiscoveryAdapter.class);
  private static final int TIMEOUT_MS = 1000;

  @Override
  public List<OnlineDevice> discoverOnlineDevices(Collection<ProbeSpecification> specs) {
    List<OnlineDevice> discoveredDevices = new ArrayList<>();
    List<InetAddress> broadcastAddresses = getBroadcastAddresses();

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(TIMEOUT_MS);

      // 1. Send all unique probes across all network interfaces
      for (ProbeSpecification spec : specs) {
        for (InetAddress broadcastAddr : broadcastAddresses) {
          try {
            DatagramPacket packet = new DatagramPacket(
                spec.payload(),
                spec.payload().length,
                broadcastAddr,
                spec.port());
            socket.send(packet);
          } catch (IOException e) {
            log.warn("Failed to send probe to interface: {}", broadcastAddr, e);
          }
        }
      }

      // 2. Listen for any replies
      byte[] receiveBuffer = new byte[1024];
      while (true) {
        try {
          DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
          socket.receive(receivePacket);

          String responseText = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
          String ip = receivePacket.getAddress().getHostAddress();
          int port = receivePacket.getPort();

          discoveredDevices.add(new OnlineDevice(responseText, ip, port));

        } catch (SocketTimeoutException e) {
          break; // No more devices replied within the timeout window
        }
      }

    } catch (IOException e) {
      log.error("Network exception during generic device discovery", e);
    }

    return discoveredDevices;
  }

  private List<InetAddress> getBroadcastAddresses() {
    List<InetAddress> broadcastList = new ArrayList<>();
    try {
      // 1. Get every physical network interface (Wi-Fi, Ethernet, Virtual switches)
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

      while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        // 2. Filter out interfaces that are offline, loopback (127.0.0.1), or virtual
        // docker interfaces
        if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
          continue;
        }
        // 3. Extract the concrete IP configuration assignments from the interface
        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
          InetAddress broadcast = interfaceAddress.getBroadcast();
          // 4. If the subnet has a valid broadcast layout, save it
          if (broadcast != null) {
            broadcastList.add(broadcast);
          }
        }
      }
    } catch (SocketException e) {
      log.error("Failed to retrieve network interfaces for device discovery", e);
    }
    // 5. Global Fallback: If no interfaces were active, try the universal local broadcast
    String universalAddress = "255.255.255.255";
    if (broadcastList.isEmpty()) {
      try {
        log.warn("No specific interface broadcast addresses found. Falling back to {}", universalAddress);
        broadcastList.add(InetAddress.getByName(universalAddress));
      } catch (UnknownHostException e) {
        log.error("Failed to resolve universal broadcast address {}. Network stack may be misconfigured.",
            universalAddress,
            e);
      }
    }

    return broadcastList;
  }
}