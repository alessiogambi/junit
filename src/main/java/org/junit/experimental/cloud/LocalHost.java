package org.junit.experimental.cloud;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import at.ac.tuwien.infosys.jcloudscale.messaging.objects.monitoring.CPUEvent;
import at.ac.tuwien.infosys.jcloudscale.messaging.objects.monitoring.RAMEvent;
import at.ac.tuwien.infosys.jcloudscale.vm.ClientCloudObject;
import at.ac.tuwien.infosys.jcloudscale.vm.IHost;

public class LocalHost implements IHost {

    private static LocalHost INSTANCE;

    private UUID id = UUID.randomUUID();

    private LocalHost() {
    }

    public synchronized static LocalHost get() {
        if (INSTANCE == null) {
            INSTANCE = new LocalHost();
        }
        return INSTANCE;
    }

    Map<UUID, ClientCloudObject> cloudObjects = new HashMap<UUID, ClientCloudObject>();

    public ClientCloudObject createClientCloudObject(Class<?> objectClass,
            Object proxyObject) {
        UUID objectId = UUID.randomUUID();
        // Not sure what this is
        long clientTemporalId = System.currentTimeMillis();
        ClientCloudObject co = new ClientCloudObject(objectId, clientTemporalId,
                objectClass, proxyObject, null);

        // System.out.println("LocalHost.createClientCloudObject() new CO: " +
        // co);
        cloudObjects.put(objectId, co);

        return co;

    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getIpAddress() {
        return "localhost";
    }

    @Override
    public Date getStartupTime() {
        return new Date(0);
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public Date getLastRequestTime() {
        return new Date(0);
    }

    @Override
    public Iterable<ClientCloudObject> getCloudObjects() {
        return cloudObjects.values();
    }

    @Override
    public ClientCloudObject getCloudObjectById(UUID objectId) {
        return cloudObjects.get(objectId);
    }

    @Override
    public int getCloudObjectsCount() {
        return cloudObjects.size();
    }

    @Override
    public CPUEvent getCurrentCPULoad() {
        return null;
    }

    @Override
    public RAMEvent getCurrentRAMUsage() {
        return null;
    }

    public ClientCloudObject getClientCloudObject(Object proxyObject) {
        for (ClientCloudObject co : cloudObjects.values())
            if (co.getProxy().equals(proxyObject))
                return co;
        return null;
    }

}
