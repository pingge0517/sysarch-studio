/**
 */
package com.example.sysarch.model.impl;

import com.example.sysarch.model.Allocate;
import com.example.sysarch.model.ArchElement;
import com.example.sysarch.model.Connection;
import com.example.sysarch.model.ConnectionKind;
import com.example.sysarch.model.DiagramLayout;
import com.example.sysarch.model.InterfaceDef;
import com.example.sysarch.model.ItemFlow;
import com.example.sysarch.model.Layer;
import com.example.sysarch.model.Part;
import com.example.sysarch.model.PartDefinition;
import com.example.sysarch.model.Port;
import com.example.sysarch.model.PortDirection;
import com.example.sysarch.model.Requirement;
import com.example.sysarch.model.SysarchFactory;
import com.example.sysarch.model.SysarchPackage;
import com.example.sysarch.model.VerifyMethod;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

import org.eclipse.emf.ecore.impl.EPackageImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model <b>Package</b>.
 * <!-- end-user-doc -->
 * @generated
 */
public class SysarchPackageImpl extends EPackageImpl implements SysarchPackage {
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass archElementEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass partDefinitionEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass partEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass portEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass connectionEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass itemFlowEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass requirementEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass interfaceDefEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass allocateEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EClass diagramLayoutEClass = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EEnum layerEEnum = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EEnum portDirectionEEnum = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EEnum connectionKindEEnum = null;

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private EEnum verifyMethodEEnum = null;

	/**
	 * Creates an instance of the model <b>Package</b>, registered with
	 * {@link org.eclipse.emf.ecore.EPackage.Registry EPackage.Registry} by the package
	 * package URI value.
	 * <p>Note: the correct way to create the package is via the static
	 * factory method {@link #init init()}, which also performs
	 * initialization of the package, or returns the registered package,
	 * if one already exists.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see org.eclipse.emf.ecore.EPackage.Registry
	 * @see com.example.sysarch.model.SysarchPackage#eNS_URI
	 * @see #init()
	 * @generated
	 */
	private SysarchPackageImpl() {
		super(eNS_URI, SysarchFactory.eINSTANCE);
	}
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private static boolean isInited = false;

	/**
	 * Creates, registers, and initializes the <b>Package</b> for this model, and for any others upon which it depends.
	 *
	 * <p>This method is used to initialize {@link SysarchPackage#eINSTANCE} when that field is accessed.
	 * Clients should not invoke it directly. Instead, they should simply access that field to obtain the package.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #eNS_URI
	 * @see #createPackageContents()
	 * @see #initializePackageContents()
	 * @generated
	 */
	public static SysarchPackage init() {
		if (isInited) return (SysarchPackage)EPackage.Registry.INSTANCE.getEPackage(SysarchPackage.eNS_URI);

		// Obtain or create and register package
		Object registeredSysarchPackage = EPackage.Registry.INSTANCE.get(eNS_URI);
		SysarchPackageImpl theSysarchPackage = registeredSysarchPackage instanceof SysarchPackageImpl ? (SysarchPackageImpl)registeredSysarchPackage : new SysarchPackageImpl();

		isInited = true;

		// Create package meta-data objects
		theSysarchPackage.createPackageContents();

		// Initialize created meta-data
		theSysarchPackage.initializePackageContents();

		// Mark meta-data to indicate it can't be changed
		theSysarchPackage.freeze();

		// Update the registry and return the package
		EPackage.Registry.INSTANCE.put(SysarchPackage.eNS_URI, theSysarchPackage);
		return theSysarchPackage;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getArchElement() {
		return archElementEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Id() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Name() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Description() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Layer() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Stereotypes() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(4);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_WorkItemId() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(5);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getArchElement_Rev() {
		return (EAttribute)archElementEClass.getEStructuralFeatures().get(6);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getPartDefinition() {
		return partDefinitionEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPartDefinition_OwnedParts() {
		return (EReference)partDefinitionEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPartDefinition_OwnedPorts() {
		return (EReference)partDefinitionEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPartDefinition_SuperTypes() {
		return (EReference)partDefinitionEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getPartDefinition_IsAbstract() {
		return (EAttribute)partDefinitionEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getPart() {
		return partEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPart_Owner() {
		return (EReference)partEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPart_TypedBy() {
		return (EReference)partEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPart_Ports() {
		return (EReference)partEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getPart_Multiplicity() {
		return (EAttribute)partEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getPort() {
		return portEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPort_Owner() {
		return (EReference)portEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPort_PartOwner() {
		return (EReference)portEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getPort_Direction() {
		return (EAttribute)portEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getPort_InterfaceType() {
		return (EReference)portEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getPort_IsConjugated() {
		return (EAttribute)portEClass.getEStructuralFeatures().get(4);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getConnection() {
		return connectionEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getConnection_Source() {
		return (EReference)connectionEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getConnection_Target() {
		return (EReference)connectionEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getConnection_Kind() {
		return (EAttribute)connectionEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getConnection_InterfaceType() {
		return (EReference)connectionEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getItemFlow() {
		return itemFlowEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getItemFlow_Connection() {
		return (EReference)itemFlowEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getItemFlow_ItemName() {
		return (EAttribute)itemFlowEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getItemFlow_ItemType() {
		return (EAttribute)itemFlowEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getRequirement() {
		return requirementEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getRequirement_ReqId() {
		return (EAttribute)requirementEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getRequirement_Text() {
		return (EAttribute)requirementEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getRequirement_VerifyMethod() {
		return (EAttribute)requirementEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getRequirement_Priority() {
		return (EAttribute)requirementEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getRequirement_SatisfiedBy() {
		return (EReference)requirementEClass.getEStructuralFeatures().get(4);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getRequirement_VerifiedBy() {
		return (EReference)requirementEClass.getEStructuralFeatures().get(5);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getInterfaceDef() {
		return interfaceDefEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getInterfaceDef_Protocol() {
		return (EAttribute)interfaceDefEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getInterfaceDef_DataType() {
		return (EAttribute)interfaceDefEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getInterfaceDef_SuperTypes() {
		return (EReference)interfaceDefEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EOperation getInterfaceDef__CompatibleWith__InterfaceDef() {
		return interfaceDefEClass.getEOperations().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getAllocate() {
		return allocateEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getAllocate_From() {
		return (EReference)allocateEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EReference getAllocate_To() {
		return (EReference)allocateEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getAllocate_Rationale() {
		return (EAttribute)allocateEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EClass getDiagramLayout() {
		return diagramLayoutEClass;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_DiagramKey() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(0);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_ElementRef() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(1);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_X() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(2);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_Y() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(3);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_Width() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(4);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_Height() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(5);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EAttribute getDiagramLayout_Waypoints() {
		return (EAttribute)diagramLayoutEClass.getEStructuralFeatures().get(6);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EEnum getLayer() {
		return layerEEnum;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EEnum getPortDirection() {
		return portDirectionEEnum;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EEnum getConnectionKind() {
		return connectionKindEEnum;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public EEnum getVerifyMethod() {
		return verifyMethodEEnum;
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	@Override
	public SysarchFactory getSysarchFactory() {
		return (SysarchFactory)getEFactoryInstance();
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private boolean isCreated = false;

	/**
	 * Creates the meta-model objects for the package.  This method is
	 * guarded to have no affect on any invocation but its first.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void createPackageContents() {
		if (isCreated) return;
		isCreated = true;

		// Create classes and their features
		archElementEClass = createEClass(ARCH_ELEMENT);
		createEAttribute(archElementEClass, ARCH_ELEMENT__ID);
		createEAttribute(archElementEClass, ARCH_ELEMENT__NAME);
		createEAttribute(archElementEClass, ARCH_ELEMENT__DESCRIPTION);
		createEAttribute(archElementEClass, ARCH_ELEMENT__LAYER);
		createEAttribute(archElementEClass, ARCH_ELEMENT__STEREOTYPES);
		createEAttribute(archElementEClass, ARCH_ELEMENT__WORK_ITEM_ID);
		createEAttribute(archElementEClass, ARCH_ELEMENT__REV);

		partDefinitionEClass = createEClass(PART_DEFINITION);
		createEReference(partDefinitionEClass, PART_DEFINITION__OWNED_PARTS);
		createEReference(partDefinitionEClass, PART_DEFINITION__OWNED_PORTS);
		createEReference(partDefinitionEClass, PART_DEFINITION__SUPER_TYPES);
		createEAttribute(partDefinitionEClass, PART_DEFINITION__IS_ABSTRACT);

		partEClass = createEClass(PART);
		createEReference(partEClass, PART__OWNER);
		createEReference(partEClass, PART__TYPED_BY);
		createEReference(partEClass, PART__PORTS);
		createEAttribute(partEClass, PART__MULTIPLICITY);

		portEClass = createEClass(PORT);
		createEReference(portEClass, PORT__OWNER);
		createEReference(portEClass, PORT__PART_OWNER);
		createEAttribute(portEClass, PORT__DIRECTION);
		createEReference(portEClass, PORT__INTERFACE_TYPE);
		createEAttribute(portEClass, PORT__IS_CONJUGATED);

		connectionEClass = createEClass(CONNECTION);
		createEReference(connectionEClass, CONNECTION__SOURCE);
		createEReference(connectionEClass, CONNECTION__TARGET);
		createEAttribute(connectionEClass, CONNECTION__KIND);
		createEReference(connectionEClass, CONNECTION__INTERFACE_TYPE);

		itemFlowEClass = createEClass(ITEM_FLOW);
		createEReference(itemFlowEClass, ITEM_FLOW__CONNECTION);
		createEAttribute(itemFlowEClass, ITEM_FLOW__ITEM_NAME);
		createEAttribute(itemFlowEClass, ITEM_FLOW__ITEM_TYPE);

		requirementEClass = createEClass(REQUIREMENT);
		createEAttribute(requirementEClass, REQUIREMENT__REQ_ID);
		createEAttribute(requirementEClass, REQUIREMENT__TEXT);
		createEAttribute(requirementEClass, REQUIREMENT__VERIFY_METHOD);
		createEAttribute(requirementEClass, REQUIREMENT__PRIORITY);
		createEReference(requirementEClass, REQUIREMENT__SATISFIED_BY);
		createEReference(requirementEClass, REQUIREMENT__VERIFIED_BY);

		interfaceDefEClass = createEClass(INTERFACE_DEF);
		createEAttribute(interfaceDefEClass, INTERFACE_DEF__PROTOCOL);
		createEAttribute(interfaceDefEClass, INTERFACE_DEF__DATA_TYPE);
		createEReference(interfaceDefEClass, INTERFACE_DEF__SUPER_TYPES);
		createEOperation(interfaceDefEClass, INTERFACE_DEF___COMPATIBLE_WITH__INTERFACEDEF);

		allocateEClass = createEClass(ALLOCATE);
		createEReference(allocateEClass, ALLOCATE__FROM);
		createEReference(allocateEClass, ALLOCATE__TO);
		createEAttribute(allocateEClass, ALLOCATE__RATIONALE);

		diagramLayoutEClass = createEClass(DIAGRAM_LAYOUT);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__DIAGRAM_KEY);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__ELEMENT_REF);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__X);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__Y);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__WIDTH);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__HEIGHT);
		createEAttribute(diagramLayoutEClass, DIAGRAM_LAYOUT__WAYPOINTS);

		// Create enums
		layerEEnum = createEEnum(LAYER);
		portDirectionEEnum = createEEnum(PORT_DIRECTION);
		connectionKindEEnum = createEEnum(CONNECTION_KIND);
		verifyMethodEEnum = createEEnum(VERIFY_METHOD);
	}

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	private boolean isInitialized = false;

	/**
	 * Complete the initialization of the package and its meta-model.  This
	 * method is guarded to have no affect on any invocation but its first.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	public void initializePackageContents() {
		if (isInitialized) return;
		isInitialized = true;

		// Initialize package
		setName(eNAME);
		setNsPrefix(eNS_PREFIX);
		setNsURI(eNS_URI);

		// Create type parameters

		// Set bounds for type parameters

		// Add supertypes to classes
		partDefinitionEClass.getESuperTypes().add(this.getArchElement());
		partEClass.getESuperTypes().add(this.getArchElement());
		portEClass.getESuperTypes().add(this.getArchElement());
		connectionEClass.getESuperTypes().add(this.getArchElement());
		itemFlowEClass.getESuperTypes().add(this.getArchElement());
		requirementEClass.getESuperTypes().add(this.getArchElement());
		interfaceDefEClass.getESuperTypes().add(this.getArchElement());
		allocateEClass.getESuperTypes().add(this.getArchElement());
		diagramLayoutEClass.getESuperTypes().add(this.getArchElement());

		// Initialize classes, features, and operations; add parameters
		initEClass(archElementEClass, ArchElement.class, "ArchElement", IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEAttribute(getArchElement_Id(), ecorePackage.getEString(), "id", null, 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_Name(), ecorePackage.getEString(), "name", null, 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_Description(), ecorePackage.getEString(), "description", null, 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_Layer(), this.getLayer(), "layer", "LOGICAL", 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_Stereotypes(), ecorePackage.getEString(), "stereotypes", null, 0, -1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_WorkItemId(), ecorePackage.getEString(), "workItemId", null, 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getArchElement_Rev(), ecorePackage.getEInt(), "rev", null, 0, 1, ArchElement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(partDefinitionEClass, PartDefinition.class, "PartDefinition", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getPartDefinition_OwnedParts(), this.getPart(), this.getPart_Owner(), "ownedParts", null, 0, -1, PartDefinition.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPartDefinition_OwnedPorts(), this.getPort(), this.getPort_Owner(), "ownedPorts", null, 0, -1, PartDefinition.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPartDefinition_SuperTypes(), this.getPartDefinition(), null, "superTypes", null, 0, -1, PartDefinition.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getPartDefinition_IsAbstract(), ecorePackage.getEBoolean(), "isAbstract", "false", 0, 1, PartDefinition.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(partEClass, Part.class, "Part", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getPart_Owner(), this.getPartDefinition(), this.getPartDefinition_OwnedParts(), "owner", null, 0, 1, Part.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPart_TypedBy(), this.getPartDefinition(), null, "typedBy", null, 1, 1, Part.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPart_Ports(), this.getPort(), this.getPort_PartOwner(), "ports", null, 0, -1, Part.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getPart_Multiplicity(), ecorePackage.getEString(), "multiplicity", "1", 0, 1, Part.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(portEClass, Port.class, "Port", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getPort_Owner(), this.getPartDefinition(), this.getPartDefinition_OwnedPorts(), "owner", null, 0, 1, Port.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPort_PartOwner(), this.getPart(), this.getPart_Ports(), "partOwner", null, 0, 1, Port.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getPort_Direction(), this.getPortDirection(), "direction", "INOUT", 0, 1, Port.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getPort_InterfaceType(), this.getInterfaceDef(), null, "interfaceType", null, 0, 1, Port.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getPort_IsConjugated(), ecorePackage.getEBoolean(), "isConjugated", "false", 0, 1, Port.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(connectionEClass, Connection.class, "Connection", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getConnection_Source(), this.getPort(), null, "source", null, 1, 1, Connection.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getConnection_Target(), this.getPort(), null, "target", null, 1, 1, Connection.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getConnection_Kind(), this.getConnectionKind(), "kind", "ASSEMBLY", 0, 1, Connection.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getConnection_InterfaceType(), this.getInterfaceDef(), null, "interfaceType", null, 0, 1, Connection.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(itemFlowEClass, ItemFlow.class, "ItemFlow", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getItemFlow_Connection(), this.getConnection(), null, "connection", null, 0, 1, ItemFlow.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getItemFlow_ItemName(), ecorePackage.getEString(), "itemName", null, 0, 1, ItemFlow.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getItemFlow_ItemType(), ecorePackage.getEString(), "itemType", null, 0, 1, ItemFlow.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(requirementEClass, Requirement.class, "Requirement", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEAttribute(getRequirement_ReqId(), ecorePackage.getEString(), "reqId", null, 0, 1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getRequirement_Text(), ecorePackage.getEString(), "text", null, 0, 1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getRequirement_VerifyMethod(), this.getVerifyMethod(), "verifyMethod", "TEST", 0, 1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getRequirement_Priority(), ecorePackage.getEInt(), "priority", "3", 0, 1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getRequirement_SatisfiedBy(), this.getArchElement(), null, "satisfiedBy", null, 0, -1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getRequirement_VerifiedBy(), this.getArchElement(), null, "verifiedBy", null, 0, -1, Requirement.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(interfaceDefEClass, InterfaceDef.class, "InterfaceDef", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEAttribute(getInterfaceDef_Protocol(), ecorePackage.getEString(), "protocol", null, 0, 1, InterfaceDef.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getInterfaceDef_DataType(), ecorePackage.getEString(), "dataType", null, 0, 1, InterfaceDef.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getInterfaceDef_SuperTypes(), this.getInterfaceDef(), null, "superTypes", null, 0, -1, InterfaceDef.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		EOperation op = initEOperation(getInterfaceDef__CompatibleWith__InterfaceDef(), ecorePackage.getEBoolean(), "compatibleWith", 0, 1, IS_UNIQUE, IS_ORDERED);
		addEParameter(op, this.getInterfaceDef(), "other", 0, 1, IS_UNIQUE, IS_ORDERED);

		initEClass(allocateEClass, Allocate.class, "Allocate", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEReference(getAllocate_From(), this.getArchElement(), null, "from", null, 1, 1, Allocate.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEReference(getAllocate_To(), this.getArchElement(), null, "to", null, 1, 1, Allocate.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getAllocate_Rationale(), ecorePackage.getEString(), "rationale", null, 0, 1, Allocate.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		initEClass(diagramLayoutEClass, DiagramLayout.class, "DiagramLayout", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
		initEAttribute(getDiagramLayout_DiagramKey(), ecorePackage.getEString(), "diagramKey", null, 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_ElementRef(), ecorePackage.getEString(), "elementRef", null, 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_X(), ecorePackage.getEInt(), "x", "0", 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_Y(), ecorePackage.getEInt(), "y", "0", 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_Width(), ecorePackage.getEInt(), "width", "180", 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_Height(), ecorePackage.getEInt(), "height", "90", 0, 1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
		initEAttribute(getDiagramLayout_Waypoints(), ecorePackage.getEString(), "waypoints", null, 0, -1, DiagramLayout.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

		// Initialize enums and add enum literals
		initEEnum(layerEEnum, Layer.class, "Layer");
		addEEnumLiteral(layerEEnum, Layer.OPERATIONAL);
		addEEnumLiteral(layerEEnum, Layer.SYSTEM);
		addEEnumLiteral(layerEEnum, Layer.LOGICAL);
		addEEnumLiteral(layerEEnum, Layer.PHYSICAL);
		addEEnumLiteral(layerEEnum, Layer.EPBS);

		initEEnum(portDirectionEEnum, PortDirection.class, "PortDirection");
		addEEnumLiteral(portDirectionEEnum, PortDirection.IN);
		addEEnumLiteral(portDirectionEEnum, PortDirection.OUT);
		addEEnumLiteral(portDirectionEEnum, PortDirection.INOUT);

		initEEnum(connectionKindEEnum, ConnectionKind.class, "ConnectionKind");
		addEEnumLiteral(connectionKindEEnum, ConnectionKind.ASSEMBLY);
		addEEnumLiteral(connectionKindEEnum, ConnectionKind.DELEGATION);
		addEEnumLiteral(connectionKindEEnum, ConnectionKind.ITEM_FLOW);

		initEEnum(verifyMethodEEnum, VerifyMethod.class, "VerifyMethod");
		addEEnumLiteral(verifyMethodEEnum, VerifyMethod.ANALYSIS);
		addEEnumLiteral(verifyMethodEEnum, VerifyMethod.INSPECTION);
		addEEnumLiteral(verifyMethodEEnum, VerifyMethod.TEST);
		addEEnumLiteral(verifyMethodEEnum, VerifyMethod.DEMONSTRATION);

		// Create resource
		createResource(eNS_URI);

		// Create annotations
		// http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot
		createPivotAnnotations();
	}

	/**
	 * Initializes the annotations for <b>http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot</b>.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	protected void createPivotAnnotations() {
		String source = "http://www.eclipse.org/emf/2002/Ecore/OCL/Pivot";
		addAnnotation
		  (archElementEClass,
		   source,
		   new String[] {
			   "NameNotEmpty", "not name.oclIsUndefined() and name.size() > 0"
		   });
		addAnnotation
		  (portEClass,
		   source,
		   new String[] {
			   "InterfaceTypeRequired", "direction = PortDirection::INOUT or not interfaceType.oclIsUndefined()"
		   });
		addAnnotation
		  (connectionEClass,
		   source,
		   new String[] {
			   "PortInterfaceAssignable", "kind = ConnectionKind::ASSEMBLY implies (not source.interfaceType.oclIsUndefined() and not target.interfaceType.oclIsUndefined() and source.interfaceType.compatibleWith(target.interfaceType))",
			   "NoSelfLoop", "source <> target",
			   "DirectionCompatible", "kind = ConnectionKind::ASSEMBLY implies (source.direction <> target.direction or source.direction = PortDirection::INOUT or target.direction = PortDirection::INOUT)"
		   });
		addAnnotation
		  (interfaceDefEClass,
		   source,
		   new String[] {
			   "ProtocolRequired", "not protocol.oclIsUndefined() and protocol.size() > 0"
		   });
		addAnnotation
		  (allocateEClass,
		   source,
		   new String[] {
			   "LayerAdjacentOnly", "(from.layer = Layer::OPERATIONAL and to.layer = Layer::SYSTEM) or (from.layer = Layer::SYSTEM and to.layer = Layer::LOGICAL) or (from.layer = Layer::LOGICAL and to.layer = Layer::PHYSICAL) or (from.layer = Layer::PHYSICAL and to.layer = Layer::EPBS)",
			   "NoSelfAllocate", "from <> to"
		   });
	}

} //SysarchPackageImpl
