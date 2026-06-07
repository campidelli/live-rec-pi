package campidelli.liverecpi.mixer.adapters.outbound.network;

import com.illposed.osc.ByteArrayListBytesReceiver;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializerAndParserBuilder;

import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OscMessageSerializer {

  private static final Logger log = LoggerFactory.getLogger(OscMessageSerializer.class);

  public byte[] serialize(OSCMessage message) {
    try {
      OSCSerializerAndParserBuilder builder = new OSCSerializerAndParserBuilder();
      ByteArrayListBytesReceiver bytesReceiver = new ByteArrayListBytesReceiver();
      builder.buildSerializer(bytesReceiver).write(message);
      return bytesReceiver.toByteArray();
    } catch (Exception e) {
      log.error("OSC serialization failed. path={}", message.getAddress(), e);
      throw new RuntimeException("OSC Serialization failure", e);
    }
  }
}