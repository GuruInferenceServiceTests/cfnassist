package tw.com.repository;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tw.com.AwsFacade;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.TooManyELBException;
import tw.com.exceptions.WrongNumberOfInstancesException;
import tw.com.exceptions.MustHaveBuildNumber;
import tw.com.providers.CloudClient;
import tw.com.providers.LoadBalancerClient;

import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Vpc;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

public class ELBRepository {
	private static final Logger logger = LoggerFactory.getLogger(ELBRepository.class);
	LoadBalancerClient elbClient;
	CloudClient cloudClient;
	VpcRepository vpcRepository;
	ResourceRepository cfnRepository;
	
	public ELBRepository(LoadBalancerClient elbClient, CloudClient cloudClient, VpcRepository vpcRepository, ResourceRepository cfnRepository) {
		this.elbClient = elbClient;
		this.vpcRepository = vpcRepository;
		this.cfnRepository = cfnRepository;
		this.cloudClient = cloudClient;
	}

	// TODO AWS Now lets us tag ELBs, so could use that to distinguish if more then one ELB found??
	public LoadBalancerDescription findELBFor(ProjectAndEnv projAndEnv) throws TooManyELBException {
		String vpcID = getVpcId(projAndEnv);
		List<LoadBalancerDescription> foundELBForVPC = new LinkedList<LoadBalancerDescription>();
		
		logger.info("Searching for load balancers for " + vpcID);
		List<LoadBalancerDescription> elbs = elbClient.describeLoadBalancers();
		for (LoadBalancerDescription elb : elbs) {
			String dnsName = elb.getDNSName();
			logger.debug("Found an ELB: " + dnsName);
			String possible = elb.getVPCId();
			if (possible!=null) {
				if (possible.equals(vpcID)) {
					logger.info(String.format("Matched ELB %s for VPC: %s", dnsName, vpcID));
					foundELBForVPC.add(elb);
				} 
			} else {
				logger.debug("No VPC ID for ELB " + dnsName);
			}
		}
		if (foundELBForVPC.size()>1) {
			throw new TooManyELBException(foundELBForVPC.size(), "Found too many elbs for vpc " + vpcID);
		}
		if (foundELBForVPC.size()==1) {
			return foundELBForVPC.get(0);
		}
		return null; // ugly but preserves current api
	}

	private String getVpcId(ProjectAndEnv projAndEnv) {
		Vpc vpc = vpcRepository.getCopyOfVpc(projAndEnv);
		String vpcID = vpc.getVpcId();
		return vpcID;
	}

	private List<Instance> addInstancesThatMatchBuildAndType(ProjectAndEnv projAndEnv, String typeTag) throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {
		if (!projAndEnv.hasBuildNumber()) {
			throw new MustHaveBuildNumber();
		}
		LoadBalancerDescription elb = findELBFor(projAndEnv);	
		List<Instance> currentInstances = elb.getInstances();
		String lbName = elb.getLoadBalancerName();
		List<Instance> allMatchingInstances = getMatchingInstances(projAndEnv, typeTag);
		List<Instance> instancesToAdd = filterBy(currentInstances, allMatchingInstances);
		if (allMatchingInstances.size()==0) {
			logger.warn(String.format("No instances matched %s and type tag %s (%s)", projAndEnv, typeTag, AwsFacade.TYPE_TAG));
		} else {	
			logger.info(String.format("Regsister matching %s instances with the LB %s ", instancesToAdd.size(),lbName));
			elbClient.registerInstances(instancesToAdd, lbName);	
		}
		return instancesToAdd;
	}

	private List<Instance> filterBy(List<Instance> currentInstances,
			List<Instance> allMatchingInstances) {
		List<Instance> result = new LinkedList<Instance>();
		for(Instance candidate : allMatchingInstances) {
			if (!currentInstances.contains(candidate)) {
				result.add(candidate);
			}
		}
		return result;
	}

	private List<Instance> getMatchingInstances(ProjectAndEnv projAndEnv,
			String type) throws WrongNumberOfInstancesException {
		Collection<String> instancesIds = cfnRepository.getInstancesFor(projAndEnv);
	
		List<Instance> instances = new LinkedList<Instance>();
		for (String id : instancesIds) {
			if (instanceHasCorrectType(type, id)) {
				logger.info(String.format("Adding instance %s as it matched %s %s",id, AwsFacade.TYPE_TAG, type));
				instances.add(new Instance(id));
			} else {
				logger.info(String.format("Not adding instance %s as did not match %s %s",id, AwsFacade.TYPE_TAG, type));
			}	
		}
		logger.info(String.format("Found %s instances matching %s and type: %s", instances.size(), projAndEnv, type));
		return instances;
	}

	// returns remaining instances
	private List<Instance> removeInstancesNotMatching(ProjectAndEnv projAndEnv, List<Instance> matchingInstances) throws MustHaveBuildNumber, TooManyELBException {	
		LoadBalancerDescription elb = findELBFor(projAndEnv);
		logger.info("Checking if instances should be removed from ELB " + elb.getLoadBalancerName());
		List<Instance> currentInstances = elb.getInstances();	
				
		List<Instance> toRemove = new LinkedList<Instance>();
		for(Instance current : currentInstances) {
			String instanceId = current.getInstanceId();
			if (matchingInstances.contains(current)) {
				logger.info("Instance matched project/env/build/type, will not be removed " + instanceId);
			} else {
				logger.info("Instance did not match, will be removed from ELB " +instanceId);
				toRemove.add(new Instance(instanceId));
			}
		}
		
		if (toRemove.isEmpty()) {
			logger.info("No instances to remove from ELB " + elb.getLoadBalancerName());
			return new LinkedList<Instance>();
		}
		return removeInstances(elb,toRemove);	
	}

	private List<Instance> removeInstances(LoadBalancerDescription elb,
			List<Instance> toRemove) {
		String loadBalancerName = elb.getLoadBalancerName();
		logger.info("Removing instances from ELB " + loadBalancerName);

		return elbClient.degisterInstancesFromLB(toRemove,loadBalancerName);

	}
	
	private boolean instanceHasCorrectType(String type, String id) throws WrongNumberOfInstancesException {
		List<Tag> tags = getTagsForInstance(id);
		for(Tag tag : tags) {
			if (tag.getKey().equals(AwsFacade.TYPE_TAG)) {
				return tag.getValue().equals(type);
			}
		}
		return false;
	}

	private List<Tag> getTagsForInstance(String id)
			throws WrongNumberOfInstancesException {
		return cloudClient.getTagsForInstance(id);
	}

	public List<Instance> updateInstancesMatchingBuild(ProjectAndEnv projAndEnv, String type) throws MustHaveBuildNumber, WrongNumberOfInstancesException, TooManyELBException {
		List<Instance> matchinginstances = addInstancesThatMatchBuildAndType(projAndEnv, type); 
		return removeInstancesNotMatching(projAndEnv, matchinginstances);	
	}

}