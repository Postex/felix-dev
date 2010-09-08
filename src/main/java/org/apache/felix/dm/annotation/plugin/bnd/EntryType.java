package org.apache.felix.dm.annotation.plugin.bnd;

/**
 * The type of each entry (lines) stored in a component descriptor.
 */
public enum EntryType
{
    Component, 
    AspectService,
    AdapterService,
    BundleAdapterService,
    ResourceAdapterService,
    FactoryConfigurationAdapterService,
    ServiceDependency, 
    ConfigurationDependency,
    BundleDependency,
    ResourceDependency,
}
