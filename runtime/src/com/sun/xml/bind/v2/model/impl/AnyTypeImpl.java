package com.sun.xml.bind.v2.model.impl;

import javax.xml.namespace.QName;

import com.sun.xml.bind.v2.WellKnownNamespace;
import com.sun.xml.bind.v2.runtime.Location;
import com.sun.xml.bind.v2.model.core.NonElement;
import com.sun.xml.bind.v2.model.core.TypeInfo;
import com.sun.xml.bind.v2.model.nav.Navigator;
import com.sun.xml.bind.v2.model.annotation.Locatable;

/**
 * {@link TypeInfo} implementation for <tt>xs:anyType</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
class AnyTypeImpl<T,C> implements NonElement<T,C> {

    private final T type;
    private final Navigator<T,C,?,?> nav;

    public AnyTypeImpl(Navigator<T,C,?,?> nav) {
        this.type = nav.ref(Object.class);
        this.nav = nav;
    }

    public QName getTypeName() {
        return name;
    }

    public T getType() {
        return type;
    }

    public Locatable getUpstream() {
        return null;
    }

    public boolean isSimpleType() {
        return false;
    }

    public Location getLocation() {
        return nav.getClassLocation(nav.asDecl(Object.class));
    }

    private static final QName name = new QName(WellKnownNamespace.XML_SCHEMA,"anyType");
}
