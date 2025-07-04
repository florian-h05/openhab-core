#set( $dt = $package.getClass().forName("java.util.Date").newInstance() )
#set( $year = $dt.getYear() + 1900 )
#set( $copyright = "Contributors to the ${vendorName} project" )
/*
 * Copyright (c) ${startYear}-${year} ${copyright}
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package ${package}.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ${bindingIdCamelCase}Configuration} class contains fields mapping thing configuration parameters.
 *
 * @author ${author} - Initial contribution
 */
@NonNullByDefault
public class ${bindingIdCamelCase}Configuration {

    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String hostname = "";
    public String password = "";
    public int refreshInterval = 600;
}
