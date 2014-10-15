package tw.com.integration;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import tw.com.EnvironmentSetupForTests;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.CannotFindVpcException;
import tw.com.repository.VpcRepository;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;

public class TestVpcRepository {

	private ProjectAndEnv mainProjectAndEnv = new ProjectAndEnv(EnvironmentSetupForTests.PROJECT, EnvironmentSetupForTests.ENV);
	private static VpcRepository repository;
	private static AmazonEC2Client ec2Client;
	
	@BeforeClass
	public static void beforeAllTestsOnce() {
		DefaultAWSCredentialsProviderChain credentialsProvider = new DefaultAWSCredentialsProviderChain();
		ec2Client = EnvironmentSetupForTests.createEC2Client(credentialsProvider);
		repository = new VpcRepository(ec2Client);
	}
	
	@Test
	public void testFindMainVpcForTests() {
		Vpc vpc = repository.getCopyOfVpc(mainProjectAndEnv);
		
		assertNotNull(vpc);
		
		List<Tag> tags = vpc.getTags();	
		List<Tag> expectedTags = createExpectedEc2TagList("Test");		
		assertTrue(tags.containsAll(expectedTags));
	}

	@Test
	public void testFindOtherVpcForTests() {
		Vpc altVpc = EnvironmentSetupForTests.findAltVpc(repository);	
		assertNotNull(altVpc);
		
		List<Tag> tags = altVpc.getTags();	

		List<Tag> expectedTags = createExpectedEc2TagList("AdditionalTest");		
		assertTrue(tags.containsAll(expectedTags));
	}
	
	@Test
	public void testCanSetAndResetIndexTagForVpc() throws CannotFindVpcException {
		repository.setVpcIndexTag(mainProjectAndEnv, "TESTVALUE");
		String result = repository.getVpcIndexTag(mainProjectAndEnv);	
		assertEquals("TESTVALUE", result);
		
		repository.setVpcIndexTag(mainProjectAndEnv, "0");
		result = repository.getVpcIndexTag(mainProjectAndEnv);	
		assertEquals("0", result);

	}
	
	@Test
	public void testCanSetAndDeleteTagOnVPC() {
			
		repository.setVpcTag(mainProjectAndEnv, "TestKey", "TestValue");
		
		List<Tag> expectedTags = new ArrayList<Tag>();
		expectedTags.add(new Tag("TestKey", "TestValue"));
		
		Vpc vpc = repository.getCopyOfVpc(mainProjectAndEnv);
		List<Tag> result = vpc.getTags();
		assertTrue(result.containsAll(expectedTags));
		
		repository.deleteVpcTag(mainProjectAndEnv, "TestKey");
		vpc = repository.getCopyOfVpc(mainProjectAndEnv);
		result = vpc.getTags();
		assertFalse(result.containsAll(expectedTags));
		
	}
	
	private List<Tag> createExpectedEc2TagList(String env) {
		List<Tag> expectedTags = new ArrayList<Tag>();
		expectedTags.add(new Tag("CFN_ASSIST_ENV", env));
		expectedTags.add(new Tag("CFN_ASSIST_PROJECT", "CfnAssist"));
		return expectedTags;
	}


}
