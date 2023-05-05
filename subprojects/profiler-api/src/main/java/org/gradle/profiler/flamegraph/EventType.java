package org.gradle.profiler.flamegraph;

import com.google.common.collect.ImmutableSet;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.item.IType;
import org.openjdk.jmc.common.item.ItemToolkit;
import org.openjdk.jmc.common.unit.BinaryPrefix;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.common.util.MemberAccessorToolkit;
import org.openjdk.jmc.flightrecorder.JfrAttributes;
import org.openjdk.jmc.flightrecorder.jdk.JdkAttributes;

import java.util.Set;

public enum EventType {
    CPU("cpu", "CPU", "samples", ValueField.COUNT, "Method Profiling Sample", "Method Profiling Sample Native"),
    ALLOCATION("allocation", "Allocation size", "kB", ValueField.ALLOCATION_SIZE, "Allocation in new TLAB", "Allocation outside TLAB"),
    MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "ns", ValueField.DURATION, "Java Monitor Blocked", "Java Thread Park"),
    IO("io", "File and Socket IO", "ns", ValueField.DURATION, "File Read", "File Write", "Socket Read", "Socket Write");

    private final String id;
    private final String displayName;
    private final String unitOfMeasure;
    private final ValueField valueField;
    private final Set<String> eventNames;

    EventType(String id, String displayName, String unitOfMeasure, ValueField valueField, String... eventNames) {
        this.id = id;
        this.displayName = displayName;
        this.unitOfMeasure = unitOfMeasure;
        this.eventNames = ImmutableSet.copyOf(eventNames);
        this.valueField = valueField;
    }

    public boolean matches(IItem event) {
        return eventNames.contains(event.getType().getName());
    }

    public long getValue(IItem event) {
        return valueField.getValue(event);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    private enum ValueField {
        COUNT {
            @Override
            public long getValue(IItem event) {
                return 1;
            }
        },
        DURATION {
            @Override
            public long getValue(IItem event) {
                IType<IItem> itemType = ItemToolkit.getItemType(event);
                IMemberAccessor<IQuantity, IItem> duration = itemType.getAccessor(JfrAttributes.DURATION.getKey());
                if (duration == null) {
                    IMemberAccessor<IQuantity, IItem> startTime = itemType.getAccessor(JfrAttributes.START_TIME.getKey());
                    IMemberAccessor<IQuantity, IItem> endTime = itemType.getAccessor(JfrAttributes.END_TIME.getKey());
                    duration = MemberAccessorToolkit.difference(endTime, startTime);
                }
                return duration.getMember(event).in(UnitLookup.NANOSECOND).longValue();
            }
        },
        ALLOCATION_SIZE {
            @Override
            public long getValue(IItem event) {
                IType<IItem> itemType = ItemToolkit.getItemType(event);
                IMemberAccessor<IQuantity, IItem> accessor = itemType.getAccessor(JdkAttributes.TLAB_SIZE.getKey());
                if (accessor == null) {
                    accessor = itemType.getAccessor(JdkAttributes.ALLOCATION_SIZE.getKey());
                }
                return accessor.getMember(event)
                    .in(UnitLookup.MEMORY.getUnit(BinaryPrefix.KIBI))
                    .longValue();
            }
        };

        public abstract long getValue(IItem event);
    }
}
