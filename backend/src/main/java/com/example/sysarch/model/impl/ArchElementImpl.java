/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.SysarchPackage;

import java.util.Collection;

import org.eclipse.emf.common.notify.Notification;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;

import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.EObjectImpl;

import org.eclipse.emf.ecore.util.EDataTypeUniqueEList;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Arch Element</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getId <em>Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getName <em>Name</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getDescription <em>Description</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getLayer <em>Layer</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getStereotypes <em>Stereotypes</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getWorkItemId <em>Work Item Id</em>}</li>
 *   <li>{@link com.example.sysarch.model.impl.ArchElementImpl#getRev <em>Rev</em>}</li>
 * </ul>
 *
 * @generated
 */
public abstract class ArchElementImpl extends EObjectImpl implements ArchElement {
	/**
	 * The default value of the '{@link #getId() <em>Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getId()
	 * @generated
	 * @ordered
	 */
	protected static final String ID_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getId() <em>Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getId()
	 * @generated
	 * @ordered
	 */
	protected String id = ID_EDEFAULT;

	/**
	 * The default value of the '{@link #getName() <em>Name</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected static final String NAME_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getName() <em>Name</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getName()
	 * @generated
	 * @ordered
	 */
	protected String name = NAME_EDEFAULT;

	/**
	 * The default value of the '{@link #getDescription() <em>Description</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDescription()
	 * @generated
	 * @ordered
	 */
	protected static final String DESCRIPTION_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getDescription() <em>Description</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getDescription()
	 * @generated
	 * @ordered
	 */
	protected String description = DESCRIPTION_EDEFAULT;

	/**
	 * The default value of the '{@link #getLayer() <em>Layer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLayer()
	 * @generated
	 * @ordered
	 */
	protected static final Layer LAYER_EDEFAULT = Layer.LOGICAL;

	/**
	 * The cached value of the '{@link #getLayer() <em>Layer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLayer()
	 * @generated
	 * @ordered
	 */
	protected Layer layer = LAYER_EDEFAULT;

	/**
	 * The cached value of the '{@link #getStereotypes() <em>Stereotypes</em>}' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getStereotypes()
	 * @generated
	 * @ordered
	 */
	protected EList<String> stereotypes;

	/**
	 * The default value of the '{@link #getWorkItemId() <em>Work Item Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWorkItemId()
	 * @generated
	 * @ordered
	 */
	protected static final String WORK_ITEM_ID_EDEFAULT = null;

	/**
	 * The cached value of the '{@link #getWorkItemId() <em>Work Item Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getWorkItemId()
	 * @generated
	 * @ordered
	 */
	protected String workItemId = WORK_ITEM_ID_EDEFAULT;

	/**
	 * The default value of the '{@link #getRev() <em>Rev</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRev()
	 * @generated
	 * @ordered
	 */
	protected static final int REV_EDEFAULT = 0;

	/**
	 * The cached value of the '{@link #getRev() <em>Rev</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRev()
	 * @generated
	 * @ordered
	 */
	protected int rev = REV_EDEFAULT;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected ArchElementImpl() {
		super();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	protected EClass eStaticClass() {
		return SysarchPackage.Literals.ARCH_ELEMENT;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setId(String newId) {
		String oldId = id;
		id = newId;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__ID, oldId, id));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setName(String newName) {
		String oldName = name;
		name = newName;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__NAME, oldName, name));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getDescription() {
		return description;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setDescription(String newDescription) {
		String oldDescription = description;
		description = newDescription;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__DESCRIPTION, oldDescription, description));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Layer getLayer() {
		return layer;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setLayer(Layer newLayer) {
		Layer oldLayer = layer;
		layer = newLayer == null ? LAYER_EDEFAULT : newLayer;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__LAYER, oldLayer, layer));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EList<String> getStereotypes() {
		if (stereotypes == null) {
			stereotypes = new EDataTypeUniqueEList<String>(String.class, this, SysarchPackage.ARCH_ELEMENT__STEREOTYPES);
		}
		return stereotypes;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public String getWorkItemId() {
		return workItemId;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setWorkItemId(String newWorkItemId) {
		String oldWorkItemId = workItemId;
		workItemId = newWorkItemId;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__WORK_ITEM_ID, oldWorkItemId, workItemId));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public int getRev() {
		return rev;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public void setRev(int newRev) {
		int oldRev = rev;
		rev = newRev;
		if (eNotificationRequired())
			eNotify(new ENotificationImpl(this, Notification.SET, SysarchPackage.ARCH_ELEMENT__REV, oldRev, rev));
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public Object eGet(int featureID, boolean resolve, boolean coreType) {
		switch (featureID) {
			case SysarchPackage.ARCH_ELEMENT__ID:
				return getId();
			case SysarchPackage.ARCH_ELEMENT__NAME:
				return getName();
			case SysarchPackage.ARCH_ELEMENT__DESCRIPTION:
				return getDescription();
			case SysarchPackage.ARCH_ELEMENT__LAYER:
				return getLayer();
			case SysarchPackage.ARCH_ELEMENT__STEREOTYPES:
				return getStereotypes();
			case SysarchPackage.ARCH_ELEMENT__WORK_ITEM_ID:
				return getWorkItemId();
			case SysarchPackage.ARCH_ELEMENT__REV:
				return getRev();
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
			case SysarchPackage.ARCH_ELEMENT__ID:
				setId((String)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__NAME:
				setName((String)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__DESCRIPTION:
				setDescription((String)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__LAYER:
				setLayer((Layer)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__STEREOTYPES:
				getStereotypes().clear();
				getStereotypes().addAll((Collection<? extends String>)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__WORK_ITEM_ID:
				setWorkItemId((String)newValue);
				return;
			case SysarchPackage.ARCH_ELEMENT__REV:
				setRev((Integer)newValue);
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
			case SysarchPackage.ARCH_ELEMENT__ID:
				setId(ID_EDEFAULT);
				return;
			case SysarchPackage.ARCH_ELEMENT__NAME:
				setName(NAME_EDEFAULT);
				return;
			case SysarchPackage.ARCH_ELEMENT__DESCRIPTION:
				setDescription(DESCRIPTION_EDEFAULT);
				return;
			case SysarchPackage.ARCH_ELEMENT__LAYER:
				setLayer(LAYER_EDEFAULT);
				return;
			case SysarchPackage.ARCH_ELEMENT__STEREOTYPES:
				getStereotypes().clear();
				return;
			case SysarchPackage.ARCH_ELEMENT__WORK_ITEM_ID:
				setWorkItemId(WORK_ITEM_ID_EDEFAULT);
				return;
			case SysarchPackage.ARCH_ELEMENT__REV:
				setRev(REV_EDEFAULT);
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
			case SysarchPackage.ARCH_ELEMENT__ID:
				return ID_EDEFAULT == null ? id != null : !ID_EDEFAULT.equals(id);
			case SysarchPackage.ARCH_ELEMENT__NAME:
				return NAME_EDEFAULT == null ? name != null : !NAME_EDEFAULT.equals(name);
			case SysarchPackage.ARCH_ELEMENT__DESCRIPTION:
				return DESCRIPTION_EDEFAULT == null ? description != null : !DESCRIPTION_EDEFAULT.equals(description);
			case SysarchPackage.ARCH_ELEMENT__LAYER:
				return layer != LAYER_EDEFAULT;
			case SysarchPackage.ARCH_ELEMENT__STEREOTYPES:
				return stereotypes != null && !stereotypes.isEmpty();
			case SysarchPackage.ARCH_ELEMENT__WORK_ITEM_ID:
				return WORK_ITEM_ID_EDEFAULT == null ? workItemId != null : !WORK_ITEM_ID_EDEFAULT.equals(workItemId);
			case SysarchPackage.ARCH_ELEMENT__REV:
				return rev != REV_EDEFAULT;
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
		result.append(" (id: ");
		result.append(id);
		result.append(", name: ");
		result.append(name);
		result.append(", description: ");
		result.append(description);
		result.append(", layer: ");
		result.append(layer);
		result.append(", stereotypes: ");
		result.append(stereotypes);
		result.append(", workItemId: ");
		result.append(workItemId);
		result.append(", rev: ");
		result.append(rev);
		result.append(')');
		return result.toString();
	}

} //ArchElementImpl
