/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.SysarchPackage;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;

import org.eclipse.emf.ecore.impl.ENotificationImpl;

import org.eclipse.emf.ecore.util.EObjectContainmentWithInverseEList;
import org.eclipse.emf.ecore.util.EObjectResolvingEList;
import org.eclipse.emf.ecore.util.InternalEList;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Part Definition</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.PartDefinitionImpl#getOwnedParts <em>Owned Parts</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PartDefinitionImpl#getOwnedPorts <em>Owned Ports</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PartDefinitionImpl#getSuperTypes <em>Super Types</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.PartDefinitionImpl#isIsAbstract <em>Is Abstract</em>}</li>
 * </ul>
 *
 * @generated
 */
public class PartDefinitionImpl extends ArchElementImpl implements PartDefinition {
	/**
	 * The cached value of the '{@link #getOwnedParts() <em>Owned Parts</em>}' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getOwnedParts()
	 * @generated
	 * @ordered
	 */
	protected EList<Part> ownedParts;

	/**
	 * The cached value of the '{@link #getOwnedPorts() <em>Owned Ports</em>}' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getOwnedPorts()
	 * @generated
	 * @ordered
	 */
	protected EList<Port> ownedPorts;

	/**
	 * The cached value of the '{@link #getSuperTypes() <em>Super Types</em>}' reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getSuperTypes()
	 * @generated
	 * @ordered
	 */
	protected EList<PartDefinition> superTypes;

	/**
	 * The default value of the '{@link #isIsAbstract() <em>Is Abstract</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsAbstract()
	 * @generated
	 * @ordered
	 */
	protected static final boolean IS_ABSTRACT_EDEFAULT = false;

	/**
	 * The cached value of the '{@link #isIsAbstract() <em>Is Abstract</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #isIsAbstract()
	 * @generated
	 * @ordered
	 */
	protected boolean isAbstract = IS_ABSTRACT_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected PartDefinitionImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.PART_DEFINITION;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<Part> getOwnedParts() {
		if (ownedParts == null) {
			ownedParts = new EObjectContainmentWithInverseEList<Part>(Part.class, this, SysarchPackage.PART_DEFINITION__OWNED_PARTS, SysarchPackage.PART__OWNER);
		}
		return ownedParts;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<Port> getOwnedPorts() {
		if (ownedPorts == null) {
			ownedPorts = new EObjectContainmentWithInverseEList<Port>(Port.class, this, SysarchPackage.PART_DEFINITION__OWNED_PORTS, SysarchPackage.PORT__OWNER);
		}
		return ownedPorts;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<PartDefinition> getSuperTypes() {
		if (superTypes == null) {
			superTypes = new EObjectResolvingEList<PartDefinition>(PartDefinition.class, this, SysarchPackage.PART_DEFINITION__SUPER_TYPES);
		}
		return superTypes;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public boolean isIsAbstract() {
		return isAbstract;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setIsAbstract(boolean newIsAbstract) {
		boolean oldIsAbstract = isAbstract;
		isAbstract = newIsAbstract;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.PART_DEFINITION__IS_ABSTRACT, oldIsAbstract, isAbstract));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public NotificationChain eInverseAdd(InternalEObject otherEnd, int featureID, NotificationChain msgs) {
		switch (featureID) {
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				return ((InternalEList<InternalEObject>)(InternalEList<?>)getOwnedParts()).basicAdd(otherEnd, msgs);
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				return ((InternalEList<InternalEObject>)(InternalEList<?>)getOwnedPorts()).basicAdd(otherEnd, msgs);
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
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				return ((InternalEList<?>)getOwnedParts()).basicRemove(otherEnd, msgs);
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				return ((InternalEList<?>)getOwnedPorts()).basicRemove(otherEnd, msgs);
		}
		return super.eInverseRemove(otherEnd, featureID, msgs);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				return getOwnedParts();
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				return getOwnedPorts();
			case SysarchPackage.PART_DEFINITION__SUPER_TYPES:
				return getSuperTypes();
			case SysarchPackage.PART_DEFINITION__IS_ABSTRACT:
				return isIsAbstract();
		}
		return super.eGet(featureID, resolve, coreType);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void eSet(int featureID, Object newValue) {
		switch (featureID) {
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				getOwnedParts().clear();
				getOwnedParts().addAll((Collection<? extends Part>)newValue);
				return;
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				getOwnedPorts().clear();
				getOwnedPorts().addAll((Collection<? extends Port>)newValue);
				return;
			case SysarchPackage.PART_DEFINITION__SUPER_TYPES:
				getSuperTypes().clear();
				getSuperTypes().addAll((Collection<? extends PartDefinition>)newValue);
				return;
			case SysarchPackage.PART_DEFINITION__IS_ABSTRACT:
				setIsAbstract((Boolean)newValue);
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
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				getOwnedParts().clear();
				return;
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				getOwnedPorts().clear();
				return;
			case SysarchPackage.PART_DEFINITION__SUPER_TYPES:
				getSuperTypes().clear();
				return;
			case SysarchPackage.PART_DEFINITION__IS_ABSTRACT:
				setIsAbstract(IS_ABSTRACT_EDEFAULT);
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
			case SysarchPackage.PART_DEFINITION__OWNED_PARTS:
				return ownedParts != null && !ownedParts.isEmpty();
			case SysarchPackage.PART_DEFINITION__OWNED_PORTS:
				return ownedPorts != null && !ownedPorts.isEmpty();
			case SysarchPackage.PART_DEFINITION__SUPER_TYPES:
				return superTypes != null && !superTypes.isEmpty();
			case SysarchPackage.PART_DEFINITION__IS_ABSTRACT:
				return isAbstract != IS_ABSTRACT_EDEFAULT;
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
		result.append(" (isAbstract: ");
		result.append(isAbstract);
		result.append(')');
		return result.toString();
	}

} //PartDefinitionImpl
