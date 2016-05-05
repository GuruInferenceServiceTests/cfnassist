package tw.com.acceptance;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DeleteKeyPairRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import tw.com.CLIArgBuilder;
import tw.com.EnvironmentSetupForTests;
import tw.com.commandline.Main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class TestKeyPairCreationAndSave {

    private static AmazonEC2Client ec2Client;

    @BeforeClass
    public static void beforeAllTestsRun() {
        DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
        ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
    }

    @Test
    public void shouldCreateKeyPairWithFilename() throws IOException {

        String filename = "testFilenameForPem.tmp";
        Path path = Paths.get(filename);
        Files.deleteIfExists(path);

        String[] args = CLIArgBuilder.createKeyPair(filename);
        Main main = new Main(args);
        int commandResult = main.parse();

        String keypairName = "CfnAssist_Test_keypair";
        DescribeKeyPairsRequest query = new DescribeKeyPairsRequest().withKeyNames(keypairName);
        DescribeKeyPairsResult keysFound = ec2Client.describeKeyPairs(query);
        List<KeyPairInfo> keys = keysFound.getKeyPairs();

        // need to make sure always delete keys

        if (keys.size()>0) {
            DeleteKeyPairRequest deleteRequest = new DeleteKeyPairRequest().withKeyName(keypairName);
            ec2Client.deleteKeyPair(deleteRequest);
        }

        // now do the asserts
        assertEquals(0, commandResult);
        assertEquals(1, keys.size());
        assertEquals(keypairName, keys.get(0).getKeyName());

        assertTrue(Files.exists(path));

        Files.deleteIfExists(path);
    }
}