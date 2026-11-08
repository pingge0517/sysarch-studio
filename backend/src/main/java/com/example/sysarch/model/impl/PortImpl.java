/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.SysarchPackage;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Port</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.PortImpl#getOwner <em>Owner</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PortImpl#getPartOwner <em>Part Owner</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PortImpl#getDirection <em>Direction</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PortImpl#getInterfaceType <em>Interface Type</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PortImpl#isIsConjugated <em>Is Conjugated</em>}</li>
 * </ul>
 *
 * @generated
 */
public class PortImpl extends ArchElementImpl implements Port {
	/**
	 * The default value of the '{@link #getDirection() <em>Direction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDirection()
	 * @generated
	 * @ordered
	 */
	protected static final PortDirection DIRECTION_EDEFAULT = PortDirection.INOUT;

	/**
	 * The cached value of the '{@link #getDirection() <em>Direction</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDirection()
	 * @generated
	 * @ordered
	 */
	protected PortDirection direction = DIRECTION_EDEFAULT;

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
	 * The default value of the '{@link #isIsConjugated() <em>Is Conjugated</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsConjugated()
	 * @generated
	 * @ordered
	 */
	protected static final boolean IS_CONJUGATED_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isIsConjugated() <em>Is Conjugated</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsConjugated()
	 * @generated
	 * @ordered
	 */
	protected boolean isConjugated = IS_CONJUGATED_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected PortImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.PORT;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public PartDefinition getOwner() {
		if (eContainerFeatureID() != SysarchPackage.PORT__OWNER) return null;
		return (PartDefinition)eInternalContainer();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public NotificationChain basicSetOwner(PartDefinition newOwner, NotificationChain msgs) {
		msgs = eBasicSetContainer((InternalEObject)newOwner, SysarchPackage.PORT__OWNER, msgs);
		return msgs;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setOwner(PartDefinition newOwner) {
		if (newOwner != eInternalContainer() || (eContainerFeatureID() != SysarchPackage.PORT__OWNER && newOwner != null)) {
			if (EcoreUtil.isAncestor(this, newOwner))
				throw new IllegalArgumentException("Recursive containment not allowed for " + toString());
			NotificationChain msgs = null;
			if (eInternalContainer() != null)
				msgs = eBasicRemoveFromContainer(msgs);
			if (newOwner != null)
				msgs = ((InternalEObject)newOwner).eInverseAdd(this, SysarchPackage.PART_DEFINITION__OWNED_PORTS, PartDefinition.class, msgs);
			msgs = basicSetOwner(newOwner, msgs);
			if (msgs != null) msgs.dispatch();
		}
		else if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PORT__OWNER, newOwner, newOwner));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Part getPartOwner() {
		if (eContainerFeatureID() != SysarchPackage.PORT__PART_OWNER) return null;
		return (Part)eInternalContainer();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public NotificationChain basicSetPartOwner(Part newPartOwner, NotificationChain msgs) {
		msgs = eBasicSetContainer((InternalEObject)newPartOwner, SysarchPackage.PORT__PART_OWNER, msgs);
		return msgs;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setPartOwner(Part newPartOwner) {
		if (newPartOwner != eInternalContainer() || (eContainerFeatureID() != SysarchPackage.PORT__PART_OWNER && newPartOwner != null)) {
			if (EcoreUtil.isAncestor(this, newPartOwner))
				throw new IllegalArgumentException("Recursive containment not allowed for " + toString());
			NotificationChain msgs = null;
			if (eInternalContainer() != null)
				msgs = eBasicRemoveFromContainer(msgs);
			if (newPartOwner != null)
				msgs = ((InternalEObject)newPartOwner).eInverseAdd(this, SysarchPackage.PART__PORTS, Part.class, msgs);
			msgs = basicSetPartOwner(newPartOwner, msgs);
			if (msgs != null) msgs.dispatch();
		}
		else if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PORT__PART_OWNER, newPartOwner, newPartOwner));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public PortDirection getDirection() {
		return direction;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setDirection(PortDirection newDirection) {
		PortDirection oldDirection = direction;
		direction = newDirection == null ? DIRECTION_EDEFAULT : newDirection;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PORT__DIRECTION, oldDirection, direction));
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
					eNotify(new ENotificationImpl(this, Notification.RESOLVE, SysarchPackage.PORT__INTERFACE_TYPE, oldInterfaceType, interfaceType));
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
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PORT__INTERFACE_TYPE, oldInterfaceType, interfaceType));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean isIsConjugated() {
		return isConjugated;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setIsConjugated(boolean newIsConjugated) {
		boolean oldIsConjugated = isConjugated;
		isConjugated = newIsConjugated;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PORT__IS_CONJUGATED, oldIsConjugated, isConjugated));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public NotificationChain eInverseAdd(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
		switch (featureID) {
			case SysarchPackage.PORT__OWNER:
				if (eInternalContainer() != null)
					msgs = eBasicRemoveFromContainer(msgs);
				return basicSetOwner((PartDefinition)otherEnd, msgs);
			case SysarchPackage.PORT__PART_OWNER:
				if (eInternalContainer() != null)
					msgs = eBasicRemoveFromContainer(msgs);
				return basicSetPartOwner((Part)otherEnd, msgs);
		}
		return super.eInverseAdd(otherEnd, featureID, msgs);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
		switch (featureID) {
			case SysarchPackage.PORT__OWNER:
				return basicSetOwner(null, msgs);
			case SysarchPackage.PORT__PART_OWNER:
				return basicSetPartOwner(null, msgs);
		}
		return super.eInverseRemove(otherEnd, featureID, msgs);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public NotificationChain eBasicRemoveFromContainerFeature(NotificationChain msgs) {
		switch (eContainerFeatureID()) {
			case SysarchPackage.PORT__OWNER:
				return eInternalContainer().eInverseRemove(this, SysarchPackage.PART_DEFINITION__OWNED_PORTS, PartDefinition.class, msgs);
			case SysarchPackage.PORT__PART_OWNER:
				return eInternalContainer().eInverseRemove(this, SysarchPackage.PART__PORTS, Part.class, msgs);
		}
		return super.eBasicRemoveFromContainerFeature(msgs);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.PORT__OWNER:
				return getOwner();
			case SysarchPackage.PORT__PART_OWNER:
				return getPartOwner();
			case SysarchPackage.PORT__DIRECTION:
				return getDirection();
			case SysarchPackage.PORT__INTERFACE_TYPE:
				if (resolve) return getInterfaceType();
				return basicGetInterfaceType();
			case SysarchPackage.PORT__IS_CONJUGATED:
				return isIsConjugated();
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
			case SysarchPackage.PORT__OWNER:
				setOwner((PartDefinition)newValue);
				return;
			case SysarchPackage.PORT__PART_OWNER:
				setPartOwner((Part)newValue);
				return;
			case SysarchPackage.PORT__DIRECTION:
				setDirection((PortDirection)newValue);
				return;
			case SysarchPackage.PORT__INTERFACE_TYPE:
				setInterfaceType((InterfaceDef)newValue);
				return;
			case SysarchPackage.PORT__IS_CONJUGATED:
				setIsConjugated((Boolean)newValue);
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
			case SysarchPackage.PORT__OWNER:
				setOwner((PartDefinition)null);
				return;
			case SysarchPackage.PORT__PART_OWNER:
				setPartOwner((Part)null);
				return;
			case SysarchPackage.PORT__DIRECTION:
				setDirection(DIRECTION_EDEFAULT);
				return;
			case SysarchPackage.PORT__INTERFACE_TYPE:
				setInterfaceType((InterfaceDef)null);
				return;
			case SysarchPackage.PORT__IS_CONJUGATED:
				setIsConjugated(IS_CONJUGATED_EDEFAULT);
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
			case SysarchPackage.PORT__OWNER:
				return getOwner() != null;
			case SysarchPackage.PORT__PART_OWNER:
				return getPartOwner() != null;
			case SysarchPackage.PORT__DIRECTION:
				return direction != DIRECTION_EDEFAULT;
			case SysarchPackage.PORT__INTERFACE_TYPE:
				return interfaceType != null;
			case SysarchPackage.PORT__IS_CONJUGATED:
				return isConjugated != IS_CONJUGATED_EDEFAULT;
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
		result.append(" (direction: ");
		result.append(direction);
		result.append(", isConjugated: ");
		result.append(isConjugated);
		result.append(')');
		return result.toString();
	}

} //PortImpl
