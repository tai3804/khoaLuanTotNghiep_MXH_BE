import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class KeyGen {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair pair = keyPairGen.generateKeyPair();
        
        PrivateKey privKey = pair.getPrivate();
        PublicKey pubKey = pair.getPublic();
        
        String privKeyString = "-----BEGIN PRIVATE KEY-----\n" + 
                               Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privKey.getEncoded()) + 
                               "\n-----END PRIVATE KEY-----\n";
        String pubKeyString = "-----BEGIN PUBLIC KEY-----\n" + 
                              Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pubKey.getEncoded()) + 
                              "\n-----END PUBLIC KEY-----\n";
                              
        Files.createDirectories(Paths.get("src/main/resources/certs"));
        Files.write(Paths.get("src/main/resources/certs/private.pem"), privKeyString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(Paths.get("src/main/resources/certs/public.pem"), pubKeyString.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Keys generated successfully");
    }
}
