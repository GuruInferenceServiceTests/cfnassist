package tw.com.providers;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.com.exceptions.WrongNumberOfInstancesException;

import java.net.InetAddress;
import java.util.*;

import static java.lang.String.format;

public class CloudClient implements ProgressListener {
    private static final Logger logger = LoggerFactory.getLogger(CloudClient.class);

    private AmazonEC2 ec2Client;
    private AwsRegionProvider regionProvider;

    public CloudClient(AmazonEC2 ec2Client, AwsRegionProvider regionProvider) {
        this.ec2Client = ec2Client;
        this.regionProvider = regionProvider;
    }

    public Vpc describeVpc(String vpcId) {
        logger.info("Get VPC by ID " + vpcId);

        DescribeVpcsRequest describeVpcsRequest = new DescribeVpcsRequest();
        Collection<String> vpcIds = new LinkedList<>();
        vpcIds.add(vpcId);
        describeVpcsRequest.setVpcIds(vpcIds);
        DescribeVpcsResult results = ec2Client.describeVpcs(describeVpcsRequest);
        return results.getVpcs().get(0);
    }

    public List<Vpc> describeVpcs() {
        logger.info("Get All VPCs");

        DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
        return describeVpcsResults.getVpcs();
    }

    public void addTagsToResources(List<String> resources, List<Tag> tags) {
        CreateTagsRequest createTagsRequest = new CreateTagsRequest(resources, tags);
        ec2Client.createTags(createTagsRequest);
    }

    public void deleteTagsFromResources(List<String> resources, Tag tag) {
        DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest().withResources(resources).withTags(tag);
        ec2Client.deleteTags(deleteTagsRequest);
    }

    public Map<String, AvailabilityZone> getAvailabilityZones() {
        String regionName = regionProvider.getRegion();
        logger.info("Get AZ for region " + regionName);
        DescribeAvailabilityZonesRequest request = new DescribeAvailabilityZonesRequest();
        Collection<Filter> filter = new LinkedList<>();
        filter.add(new Filter("region-name", Arrays.asList(regionName)));
        request.setFilters(filter);

        DescribeAvailabilityZonesResult result = ec2Client.describeAvailabilityZones(request);
        List<AvailabilityZone> zones = result.getAvailabilityZones();
        logger.info(format("Found %s zones", zones.size()));

        Map<String, AvailabilityZone> zoneMap = new HashMap<>();
        zones.forEach(zone -> zoneMap.put(zone.getZoneName().replace(zone.getRegionName(), ""), zone));
        return zoneMap;
    }

    public com.amazonaws.services.ec2.model.Instance getInstanceById(String id) throws WrongNumberOfInstancesException {
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(id);
        DescribeInstancesResult result = ec2Client.describeInstances(request);
        List<Reservation> res = result.getReservations();
        if (res.size() != 1) {
            throw new WrongNumberOfInstancesException(id, res.size());
        }
        List<com.amazonaws.services.ec2.model.Instance> ins = res.get(0).getInstances();
        if (ins.size() != 1) {
            throw new WrongNumberOfInstancesException(id, ins.size());
        }
        return ins.get(0);
    }

    public List<Vpc> getVpcs() {
        DescribeVpcsResult describeVpcsResults = ec2Client.describeVpcs();
        return describeVpcsResults.getVpcs();
    }

    public List<Subnet> getAllSubnets() {
        DescribeSubnetsResult describeResults = ec2Client.describeSubnets();
        return describeResults.getSubnets();
    }

    public List<SecurityGroup> getSecurityGroups() {
        DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups();
        return result.getSecurityGroups();
    }

    public List<NetworkAcl> getACLs() {
        DescribeNetworkAclsResult result = ec2Client.describeNetworkAcls();
        return result.getNetworkAcls();
    }

    public List<Instance> getInstances() {
        List<Instance> instances = new LinkedList<>();
        DescribeInstancesResult result = ec2Client.describeInstances();
        List<Reservation> reservations = result.getReservations();
        for (Reservation res : reservations) {
            instances.addAll(res.getInstances());
        }
        return instances;
    }

    public List<RouteTable> getRouteTables() {
        DescribeRouteTablesResult result = ec2Client.describeRouteTables();
        return result.getRouteTables();
    }

    public List<Address> getEIPs() {
        DescribeAddressesResult result = ec2Client.describeAddresses();
        return result.getAddresses();
    }

    public void addIpsToSecGroup(String secGroupId, Integer port, List<InetAddress> addresses) {
        logger.info(format("Add addresses %s for port %s to group %s", addresses, port.toString(), secGroupId));

        AuthorizeSecurityGroupIngressRequest request = new AuthorizeSecurityGroupIngressRequest();
        request.setGroupId(secGroupId);
        request.setIpPermissions(createPermissions(port, addresses));

        request.setGeneralProgressListener(this);
        ec2Client.authorizeSecurityGroupIngress(request);
    }

    public void deleteIpFromSecGroup(String groupId, Integer port, List<InetAddress> addresses) {
        logger.info(format("Remove addresses %s for port %s on group %s", addresses, port.toString(), groupId));
        RevokeSecurityGroupIngressRequest request = new RevokeSecurityGroupIngressRequest();
        request.setGroupId(groupId);
        request.setIpPermissions(createPermissions(port, addresses));
        request.setGeneralProgressListener(this);
        ec2Client.revokeSecurityGroupIngress(request);
    }

    private Collection<IpPermission> createPermissions(Integer port, List<InetAddress> addresses) {

        Collection<IpPermission> ipPermissions = new LinkedList<>();
        addresses.forEach(address ->{
            IpPermission permission = new IpPermission();
            IpRange ipRange = new IpRange().withCidrIp(format("%s/32", address.getHostAddress()));
            permission.withFromPort(port).withToPort(port).withIpProtocol("tcp").withIpv4Ranges(ipRange);
            ipPermissions.add(permission);
        });

        return ipPermissions;
    }

    @Override
    public void progressChanged(ProgressEvent progressEvent) {
        if (progressEvent.getEventType() == ProgressEventType.CLIENT_REQUEST_FAILED_EVENT) {
            logger.warn(progressEvent.toString());
        }
        logger.info(progressEvent.toString());
    }


    public KeyPair createKeyPair(String keypairName) {
        logger.info("Create keypair with name " + keypairName);
        CreateKeyPairRequest request = new CreateKeyPairRequest().withKeyName(keypairName);
        CreateKeyPairResult result = ec2Client.createKeyPair(request);
        KeyPair keyPair = result.getKeyPair();
        logger.info(format("Created keypair %s with fingerprint %s", keyPair.getKeyName(), keyPair.getKeyFingerprint()));
        return keyPair;
    }

}
