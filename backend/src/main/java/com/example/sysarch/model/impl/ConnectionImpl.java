/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchPackage;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Connection</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.ConnectionImpl#getSource <em>Source</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ConnectionImpl#getTarget <em>Target</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ConnectionImpl#getKind <em>Kind</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ConnectionImpl#getInterfaceType <em>Interface Type</em>}</li>
 * </ul>
 *
 * @generated
 */
public class ConnectionImpl extends ArchElementImpl implements Connection {
	/**
	 * The cached value of the '{@link #getSource() <em>Source</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSource()
	 * @generated
	 * @ordered
	 */
	protected Port source;

	/**
	 * The cached value of the '{@link #getTarget() <em>Target</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTarget()
	 * @generated
	 * @ordered
	 */
	protected Port target;

	/**
	 * The default value of the '{@link #getKind() <em>Kind</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKind()
	 * @generated
	 * @ordered
	 */
	protected static final ConnectionKind KIND_EDEFAULT = ConnectionKind.ASSEMBLY;

	/**
	 * The cached value of the '{@link #getKind() <em>Kind</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKind()
	 * @generated
	 * @ordered
	 */
	protected ConnectionKind kind = KIND_EDEFAULT;

	/**
	 * The cached value of the '{@link #getInterfaceType() <em>Interface Type</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getInterfaceType()
	 * @generated
	 * @ordered
	 */
	protected InterfaceDef interfaceType;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected ConnectionImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.CONNECTION;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Port getSource() {
		if (source != null && source.eIsProxy()) {
			InternalEObject oldSource = (InternalEObject)source;
			source = (Port)eResolveProxy(oldSource);
			if (source != oldSource) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.CONNECTION__SOURCE, oldSource, source));
			}
		}
		return source;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Port basicGetSource() {
		return source;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setSource(Port newSource) {
		Port oldSource = source;
		source = newSource;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.CONNECTION__SOURCE, oldSource, source));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Port getTarget() {
		if (target != null && target.eIsProxy()) {
			InternalEObject oldTarget = (InternalEObject)target;
			target = (Port)eResolveProxy(oldTarget);
			if (target != oldTarget) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.CONNECTION__TARGET, oldTarget, target));
			}
		}
		return target;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public Port basicGetTarget() {
		return target;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setTarget(Port newTarget) {
		Port oldTarget = target;
		target = newTarget;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.CONNECTION__TARGET, oldTarget, target));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public ConnectionKind getKind() {
		return kind;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setKind(ConnectionKind newKind) {
		ConnectionKind oldKind = kind;
		kind = newKind == null ? KIND_EDEFAULT : newKind;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.CONNECTION__KIND, oldKind, kind));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public InterfaceDef getInterfaceType() {
		if (interfaceType != null && interfaceType.eIsProxy()) {
			InternalEObject oldInterfaceType = (InternalEObject)interfaceType;
			interfaceType = (InterfaceDef)eResolveProxy(oldInterfaceType);
			if (interfaceType != oldInterfaceType) {
				if (eNotificationRequired())
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.CONNECTION__INTERFACE_TYPE, oldInterfaceType, interfaceType));
			}
		}
		return interfaceType;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public InterfaceDef basicGetInterfaceType() {
		return interfaceType;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setInterfaceType(InterfaceDef newInterfaceType) {
		InterfaceDef oldInterfaceType = interfaceType;
		interfaceType = newInterfaceType;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.CONNECTION__INTERFACE_TYPE, oldInterfaceType, interfaceType));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.CONNECTION__SOURCE:
				if (resolve) return getSource();
				return basicGetSource();
			case SysarchPackage.CONNECTION__TARGET:
				if (resolve) return getTarget();
				return basicGetTarget();
			case SysarchPackage.CONNECTION__KIND:
				return getKind();
			case SysarchPackage.CONNECTION__INTERFACE_TYPE:
				if (resolve) return getInterfaceType();
				return basicGetInterfaceType();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case SysarchPackage.CONNECTION__SOURCE:
				setSource((Port)newValue);
				return;
			case SysarchPackage.CONNECTION__TARGET:
				setTarget((Port)newValue);
				return;
			case SysarchPackage.CONNECTION__KIND:
				setKind((ConnectionKind)newValue);
				return;
			case SysarchPackage.CONNECTION__INTERFACE_TYPE:
				setInterfaceType((InterfaceDef)newValue);
				return;
		}
		super.eSet(featureID, newValue);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void eUnset(int featureID) {
		switch (featureID) {
			case SysarchPackage.CONNECTION__SOURCE:
				setSource((Port)null);
				return;
			case SysarchPackage.CONNECTION__TARGET:
				setTarget((Port)null);
				return;
			case SysarchPackage.CONNECTION__KIND:
				setKind(KIND_EDEFAULT);
				return;
			case SysarchPackage.CONNECTION__INTERFACE_TYPE:
				setInterfaceType((InterfaceDef)null);
				return;
		}
		super.eUnset(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean eIsSet(int featureID) {
		switch (featureID) {
			case SysarchPackage.CONNECTION__SOURCE:
				return source != null;
			case SysarchPackage.CONNECTION__TARGET:
				return target != null;
			case SysarchPackage.CONNECTION__KIND:
				return kind != KIND_EDEFAULT;
			case SysarchPackage.CONNECTION__INTERFACE_TYPE:
				return interfaceType != null;
		}
		return super.eIsSet(featureID);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (kind: ");
		result.append(kind);
		result.append(')');
		return result.toString();
	}

} //ConnectionImpl
