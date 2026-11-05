/**
 */
package com.example.sysarch.model;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

/**
 * <!-- begin-user-doc -->
 * The <b>Package</b> for the model.
 * It contains accessors for the meta objects to represent
 * <ul>
 *   <li>each class,</li>
 *   <li>each feature of each class,</li>
 *   <li>each operation of each class,</li>
 *   <li>each enum,</li>
 *   <li>and each data type</li>
 * </ul>
 * <!-- end-user-doc -->
 * <!-- begin-model-doc -->
 * 系统架构建模领域元模型：SysML v2 结构子集 + Arcadia 分层标记
 * <!-- end-model-doc -->
 * @see com.example.sysarch.model.SysarchFactory
 * @model kind="package"
 * @generated
 */
public interface SysarchPackage extends EPackage {
	/**
	 * The package name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNAME = "sysarch";

	/**
	 * The package namespace URI.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNS_URI = "http://example.com/sysarch/1.0";

	/**
	 * The package namespace name.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	String eNS_PREFIX = "sysarch";

	/**
	 * The singleton instance of the package.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	SysarchPackage eINSTANCE = com.example.sysarch.model.impl.SysarchPackageImpl.init();

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.ArchElementImpl <em>Arch Element</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.ArchElementImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getArchElement()
	 * @generated
	 */
	int ARCH_ELEMENT = 0;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__ID = 0;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__NAME = 1;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__DESCRIPTION = 2;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__LAYER = 3;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__STEREOTYPES = 4;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__WORK_ITEM_ID = 5;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT__REV = 6;

	/**
	 * The number of structural features of the '<em>Arch Element</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT_FEATURE_COUNT = 7;

	/**
	 * The number of operations of the '<em>Arch Element</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ARCH_ELEMENT_OPERATION_COUNT = 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.PartDefinitionImpl <em>Part Definition</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.PartDefinitionImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPartDefinition()
	 * @generated
	 */
	int PART_DEFINITION = 1;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Owned Parts</b></em>' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__OWNED_PARTS = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Owned Ports</b></em>' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__OWNED_PORTS = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Super Types</b></em>' reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__SUPER_TYPES = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Is Abstract</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION__IS_ABSTRACT = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of structural features of the '<em>Part Definition</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The number of operations of the '<em>Part Definition</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_DEFINITION_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.PartImpl <em>Part</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.PartImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPart()
	 * @generated
	 */
	int PART = 2;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Owner</b></em>' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__OWNER = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Typed By</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__TYPED_BY = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Ports</b></em>' containment reference list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__PORTS = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Multiplicity</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART__MULTIPLICITY = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of structural features of the '<em>Part</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The number of operations of the '<em>Part</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PART_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.PortImpl <em>Port</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.PortImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPort()
	 * @generated
	 */
	int PORT = 3;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Owner</b></em>' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__OWNER = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Part Owner</b></em>' container reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__PART_OWNER = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Direction</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__DIRECTION = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Interface Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__INTERFACE_TYPE = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The feature id for the '<em><b>Is Conjugated</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT__IS_CONJUGATED = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The number of structural features of the '<em>Port</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 5;

	/**
	 * The number of operations of the '<em>Port</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int PORT_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.ConnectionImpl <em>Connection</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.ConnectionImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getConnection()
	 * @generated
	 */
	int CONNECTION = 4;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Source</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__SOURCE = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Target</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__TARGET = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Kind</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__KIND = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Interface Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION__INTERFACE_TYPE = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of structural features of the '<em>Connection</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The number of operations of the '<em>Connection</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int CONNECTION_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.ItemFlowImpl <em>Item Flow</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.ItemFlowImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getItemFlow()
	 * @generated
	 */
	int ITEM_FLOW = 5;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Connection</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__CONNECTION = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Item Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__ITEM_NAME = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Item Type</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW__ITEM_TYPE = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The number of structural features of the '<em>Item Flow</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of operations of the '<em>Item Flow</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ITEM_FLOW_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.RequirementImpl <em>Requirement</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.RequirementImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getRequirement()
	 * @generated
	 */
	int REQUIREMENT = 6;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Req Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__REQ_ID = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Text</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__TEXT = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Verify Method</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__VERIFY_METHOD = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Priority</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT__PRIORITY = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of structural features of the '<em>Requirement</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The number of operations of the '<em>Requirement</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int REQUIREMENT_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.AllocateImpl <em>Allocate</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.AllocateImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getAllocate()
	 * @generated
	 */
	int ALLOCATE = 7;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>From</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__FROM = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>To</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__TO = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>Rationale</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE__RATIONALE = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The number of structural features of the '<em>Allocate</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The number of operations of the '<em>Allocate</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int ALLOCATE_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.impl.DiagramLayoutImpl <em>Diagram Layout</em>}' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.impl.DiagramLayoutImpl
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getDiagramLayout()
	 * @generated
	 */
	int DIAGRAM_LAYOUT = 8;

	/**
	 * The feature id for the '<em><b>Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__ID = ARCH_ELEMENT__ID;

	/**
	 * The feature id for the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__NAME = ARCH_ELEMENT__NAME;

	/**
	 * The feature id for the '<em><b>Description</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__DESCRIPTION = ARCH_ELEMENT__DESCRIPTION;

	/**
	 * The feature id for the '<em><b>Layer</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__LAYER = ARCH_ELEMENT__LAYER;

	/**
	 * The feature id for the '<em><b>Stereotypes</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__STEREOTYPES = ARCH_ELEMENT__STEREOTYPES;

	/**
	 * The feature id for the '<em><b>Work Item Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__WORK_ITEM_ID = ARCH_ELEMENT__WORK_ITEM_ID;

	/**
	 * The feature id for the '<em><b>Rev</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__REV = ARCH_ELEMENT__REV;

	/**
	 * The feature id for the '<em><b>Diagram Key</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__DIAGRAM_KEY = ARCH_ELEMENT_FEATURE_COUNT + 0;

	/**
	 * The feature id for the '<em><b>Element Ref</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__ELEMENT_REF = ARCH_ELEMENT_FEATURE_COUNT + 1;

	/**
	 * The feature id for the '<em><b>X</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__X = ARCH_ELEMENT_FEATURE_COUNT + 2;

	/**
	 * The feature id for the '<em><b>Y</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__Y = ARCH_ELEMENT_FEATURE_COUNT + 3;

	/**
	 * The feature id for the '<em><b>Width</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__WIDTH = ARCH_ELEMENT_FEATURE_COUNT + 4;

	/**
	 * The feature id for the '<em><b>Height</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__HEIGHT = ARCH_ELEMENT_FEATURE_COUNT + 5;

	/**
	 * The feature id for the '<em><b>Waypoints</b></em>' attribute list.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT__WAYPOINTS = ARCH_ELEMENT_FEATURE_COUNT + 6;

	/**
	 * The number of structural features of the '<em>Diagram Layout</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT_FEATURE_COUNT = ARCH_ELEMENT_FEATURE_COUNT + 7;

	/**
	 * The number of operations of the '<em>Diagram Layout</em>' class.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 * @ordered
	 */
	int DIAGRAM_LAYOUT_OPERATION_COUNT = ARCH_ELEMENT_OPERATION_COUNT + 0;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.Layer <em>Layer</em>}' enum.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.Layer
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getLayer()
	 * @generated
	 */
	int LAYER = 9;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.PortDirection <em>Port Direction</em>}' enum.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.PortDirection
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPortDirection()
	 * @generated
	 */
	int PORT_DIRECTION = 10;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.ConnectionKind <em>Connection Kind</em>}' enum.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.ConnectionKind
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getConnectionKind()
	 * @generated
	 */
	int CONNECTION_KIND = 11;

	/**
	 * The meta object id for the '{@link com.example.sysarch.model.VerifyMethod <em>Verify Method</em>}' enum.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see com.example.sysarch.model.VerifyMethod
	 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getVerifyMethod()
	 * @generated
	 */
	int VERIFY_METHOD = 12;


	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.ArchElement <em>Arch Element</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Arch Element</em>'.
	 * @see com.example.sysarch.model.ArchElement
	 * @generated
	 */
	EClass getArchElement();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getId <em>Id</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Id</em>'.
	 * @see com.example.sysarch.model.ArchElement#getId()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Id();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getName <em>Name</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Name</em>'.
	 * @see com.example.sysarch.model.ArchElement#getName()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Name();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getDescription <em>Description</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Description</em>'.
	 * @see com.example.sysarch.model.ArchElement#getDescription()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Description();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getLayer <em>Layer</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Layer</em>'.
	 * @see com.example.sysarch.model.ArchElement#getLayer()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Layer();

	/**
	 * Returns the meta object for the attribute list '{@link com.example.sysarch.model.ArchElement#getStereotypes <em>Stereotypes</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute list '<em>Stereotypes</em>'.
	 * @see com.example.sysarch.model.ArchElement#getStereotypes()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Stereotypes();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getWorkItemId <em>Work Item Id</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Work Item Id</em>'.
	 * @see com.example.sysarch.model.ArchElement#getWorkItemId()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_WorkItemId();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ArchElement#getRev <em>Rev</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Rev</em>'.
	 * @see com.example.sysarch.model.ArchElement#getRev()
	 * @see #getArchElement()
	 * @generated
	 */
	EAttribute getArchElement_Rev();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.PartDefinition <em>Part Definition</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Part Definition</em>'.
	 * @see com.example.sysarch.model.PartDefinition
	 * @generated
	 */
	EClass getPartDefinition();

	/**
	 * Returns the meta object for the containment reference list '{@link com.example.sysarch.model.PartDefinition#getOwnedParts <em>Owned Parts</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the containment reference list '<em>Owned Parts</em>'.
	 * @see com.example.sysarch.model.PartDefinition#getOwnedParts()
	 * @see #getPartDefinition()
	 * @generated
	 */
	EReference getPartDefinition_OwnedParts();

	/**
	 * Returns the meta object for the containment reference list '{@link com.example.sysarch.model.PartDefinition#getOwnedPorts <em>Owned Ports</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the containment reference list '<em>Owned Ports</em>'.
	 * @see com.example.sysarch.model.PartDefinition#getOwnedPorts()
	 * @see #getPartDefinition()
	 * @generated
	 */
	EReference getPartDefinition_OwnedPorts();

	/**
	 * Returns the meta object for the reference list '{@link com.example.sysarch.model.PartDefinition#getSuperTypes <em>Super Types</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference list '<em>Super Types</em>'.
	 * @see com.example.sysarch.model.PartDefinition#getSuperTypes()
	 * @see #getPartDefinition()
	 * @generated
	 */
	EReference getPartDefinition_SuperTypes();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.PartDefinition#isIsAbstract <em>Is Abstract</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Is Abstract</em>'.
	 * @see com.example.sysarch.model.PartDefinition#isIsAbstract()
	 * @see #getPartDefinition()
	 * @generated
	 */
	EAttribute getPartDefinition_IsAbstract();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.Part <em>Part</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Part</em>'.
	 * @see com.example.sysarch.model.Part
	 * @generated
	 */
	EClass getPart();

	/**
	 * Returns the meta object for the container reference '{@link com.example.sysarch.model.Part#getOwner <em>Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the container reference '<em>Owner</em>'.
	 * @see com.example.sysarch.model.Part#getOwner()
	 * @see #getPart()
	 * @generated
	 */
	EReference getPart_Owner();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.Part#getTypedBy <em>Typed By</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Typed By</em>'.
	 * @see com.example.sysarch.model.Part#getTypedBy()
	 * @see #getPart()
	 * @generated
	 */
	EReference getPart_TypedBy();

	/**
	 * Returns the meta object for the containment reference list '{@link com.example.sysarch.model.Part#getPorts <em>Ports</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the containment reference list '<em>Ports</em>'.
	 * @see com.example.sysarch.model.Part#getPorts()
	 * @see #getPart()
	 * @generated
	 */
	EReference getPart_Ports();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Part#getMultiplicity <em>Multiplicity</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Multiplicity</em>'.
	 * @see com.example.sysarch.model.Part#getMultiplicity()
	 * @see #getPart()
	 * @generated
	 */
	EAttribute getPart_Multiplicity();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.Port <em>Port</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Port</em>'.
	 * @see com.example.sysarch.model.Port
	 * @generated
	 */
	EClass getPort();

	/**
	 * Returns the meta object for the container reference '{@link com.example.sysarch.model.Port#getOwner <em>Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the container reference '<em>Owner</em>'.
	 * @see com.example.sysarch.model.Port#getOwner()
	 * @see #getPort()
	 * @generated
	 */
	EReference getPort_Owner();

	/**
	 * Returns the meta object for the container reference '{@link com.example.sysarch.model.Port#getPartOwner <em>Part Owner</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the container reference '<em>Part Owner</em>'.
	 * @see com.example.sysarch.model.Port#getPartOwner()
	 * @see #getPort()
	 * @generated
	 */
	EReference getPort_PartOwner();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Port#getDirection <em>Direction</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Direction</em>'.
	 * @see com.example.sysarch.model.Port#getDirection()
	 * @see #getPort()
	 * @generated
	 */
	EAttribute getPort_Direction();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Port#getInterfaceType <em>Interface Type</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Interface Type</em>'.
	 * @see com.example.sysarch.model.Port#getInterfaceType()
	 * @see #getPort()
	 * @generated
	 */
	EAttribute getPort_InterfaceType();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Port#isIsConjugated <em>Is Conjugated</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Is Conjugated</em>'.
	 * @see com.example.sysarch.model.Port#isIsConjugated()
	 * @see #getPort()
	 * @generated
	 */
	EAttribute getPort_IsConjugated();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.Connection <em>Connection</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Connection</em>'.
	 * @see com.example.sysarch.model.Connection
	 * @generated
	 */
	EClass getConnection();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.Connection#getSource <em>Source</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Source</em>'.
	 * @see com.example.sysarch.model.Connection#getSource()
	 * @see #getConnection()
	 * @generated
	 */
	EReference getConnection_Source();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.Connection#getTarget <em>Target</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Target</em>'.
	 * @see com.example.sysarch.model.Connection#getTarget()
	 * @see #getConnection()
	 * @generated
	 */
	EReference getConnection_Target();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Connection#getKind <em>Kind</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Kind</em>'.
	 * @see com.example.sysarch.model.Connection#getKind()
	 * @see #getConnection()
	 * @generated
	 */
	EAttribute getConnection_Kind();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Connection#getInterfaceType <em>Interface Type</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Interface Type</em>'.
	 * @see com.example.sysarch.model.Connection#getInterfaceType()
	 * @see #getConnection()
	 * @generated
	 */
	EAttribute getConnection_InterfaceType();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.ItemFlow <em>Item Flow</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Item Flow</em>'.
	 * @see com.example.sysarch.model.ItemFlow
	 * @generated
	 */
	EClass getItemFlow();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.ItemFlow#getConnection <em>Connection</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>Connection</em>'.
	 * @see com.example.sysarch.model.ItemFlow#getConnection()
	 * @see #getItemFlow()
	 * @generated
	 */
	EReference getItemFlow_Connection();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ItemFlow#getItemName <em>Item Name</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Item Name</em>'.
	 * @see com.example.sysarch.model.ItemFlow#getItemName()
	 * @see #getItemFlow()
	 * @generated
	 */
	EAttribute getItemFlow_ItemName();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.ItemFlow#getItemType <em>Item Type</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Item Type</em>'.
	 * @see com.example.sysarch.model.ItemFlow#getItemType()
	 * @see #getItemFlow()
	 * @generated
	 */
	EAttribute getItemFlow_ItemType();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.Requirement <em>Requirement</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Requirement</em>'.
	 * @see com.example.sysarch.model.Requirement
	 * @generated
	 */
	EClass getRequirement();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Requirement#getReqId <em>Req Id</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Req Id</em>'.
	 * @see com.example.sysarch.model.Requirement#getReqId()
	 * @see #getRequirement()
	 * @generated
	 */
	EAttribute getRequirement_ReqId();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Requirement#getText <em>Text</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Text</em>'.
	 * @see com.example.sysarch.model.Requirement#getText()
	 * @see #getRequirement()
	 * @generated
	 */
	EAttribute getRequirement_Text();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Requirement#getVerifyMethod <em>Verify Method</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Verify Method</em>'.
	 * @see com.example.sysarch.model.Requirement#getVerifyMethod()
	 * @see #getRequirement()
	 * @generated
	 */
	EAttribute getRequirement_VerifyMethod();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Requirement#getPriority <em>Priority</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Priority</em>'.
	 * @see com.example.sysarch.model.Requirement#getPriority()
	 * @see #getRequirement()
	 * @generated
	 */
	EAttribute getRequirement_Priority();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.Allocate <em>Allocate</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Allocate</em>'.
	 * @see com.example.sysarch.model.Allocate
	 * @generated
	 */
	EClass getAllocate();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.Allocate#getFrom <em>From</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>From</em>'.
	 * @see com.example.sysarch.model.Allocate#getFrom()
	 * @see #getAllocate()
	 * @generated
	 */
	EReference getAllocate_From();

	/**
	 * Returns the meta object for the reference '{@link com.example.sysarch.model.Allocate#getTo <em>To</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the reference '<em>To</em>'.
	 * @see com.example.sysarch.model.Allocate#getTo()
	 * @see #getAllocate()
	 * @generated
	 */
	EReference getAllocate_To();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.Allocate#getRationale <em>Rationale</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Rationale</em>'.
	 * @see com.example.sysarch.model.Allocate#getRationale()
	 * @see #getAllocate()
	 * @generated
	 */
	EAttribute getAllocate_Rationale();

	/**
	 * Returns the meta object for class '{@link com.example.sysarch.model.DiagramLayout <em>Diagram Layout</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for class '<em>Diagram Layout</em>'.
	 * @see com.example.sysarch.model.DiagramLayout
	 * @generated
	 */
	EClass getDiagramLayout();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getDiagramKey <em>Diagram Key</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Diagram Key</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getDiagramKey()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_DiagramKey();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getElementRef <em>Element Ref</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Element Ref</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getElementRef()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_ElementRef();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getX <em>X</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>X</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getX()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_X();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getY <em>Y</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Y</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getY()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_Y();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getWidth <em>Width</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Width</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getWidth()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_Width();

	/**
	 * Returns the meta object for the attribute '{@link com.example.sysarch.model.DiagramLayout#getHeight <em>Height</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute '<em>Height</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getHeight()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_Height();

	/**
	 * Returns the meta object for the attribute list '{@link com.example.sysarch.model.DiagramLayout#getWaypoints <em>Waypoints</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for the attribute list '<em>Waypoints</em>'.
	 * @see com.example.sysarch.model.DiagramLayout#getWaypoints()
	 * @see #getDiagramLayout()
	 * @generated
	 */
	EAttribute getDiagramLayout_Waypoints();

	/**
	 * Returns the meta object for enum '{@link com.example.sysarch.model.Layer <em>Layer</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for enum '<em>Layer</em>'.
	 * @see com.example.sysarch.model.Layer
	 * @generated
	 */
	EEnum getLayer();

	/**
	 * Returns the meta object for enum '{@link com.example.sysarch.model.PortDirection <em>Port Direction</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for enum '<em>Port Direction</em>'.
	 * @see com.example.sysarch.model.PortDirection
	 * @generated
	 */
	EEnum getPortDirection();

	/**
	 * Returns the meta object for enum '{@link com.example.sysarch.model.ConnectionKind <em>Connection Kind</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for enum '<em>Connection Kind</em>'.
	 * @see com.example.sysarch.model.ConnectionKind
	 * @generated
	 */
	EEnum getConnectionKind();

	/**
	 * Returns the meta object for enum '{@link com.example.sysarch.model.VerifyMethod <em>Verify Method</em>}'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the meta object for enum '<em>Verify Method</em>'.
	 * @see com.example.sysarch.model.VerifyMethod
	 * @generated
	 */
	EEnum getVerifyMethod();

	/**
	 * Returns the factory that creates the instances of the model.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the factory that creates the instances of the model.
	 * @generated
	 */
	SysarchFactory getSysarchFactory();

	/**
	 * <!-- begin-user-doc -->
	 * Defines literals for the meta objects that represent
	 * <ul>
	 *   <li>each class,</li>
	 *   <li>each feature of each class,</li>
	 *   <li>each operation of each class,</li>
	 *   <li>each enum,</li>
	 *   <li>and each data type</li>
	 * </ul>
	 * <!-- end-user-doc -->
	 * @generated
	 */
	interface Literals {
		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.ArchElementImpl <em>Arch Element</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.ArchElementImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getArchElement()
		 * @generated
		 */
		EClass ARCH_ELEMENT = eINSTANCE.getArchElement();

		/**
		 * The meta object literal for the '<em><b>Id</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__ID = eINSTANCE.getArchElement_Id();

		/**
		 * The meta object literal for the '<em><b>Name</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__NAME = eINSTANCE.getArchElement_Name();

		/**
		 * The meta object literal for the '<em><b>Description</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__DESCRIPTION = eINSTANCE.getArchElement_Description();

		/**
		 * The meta object literal for the '<em><b>Layer</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__LAYER = eINSTANCE.getArchElement_Layer();

		/**
		 * The meta object literal for the '<em><b>Stereotypes</b></em>' attribute list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__STEREOTYPES = eINSTANCE.getArchElement_Stereotypes();

		/**
		 * The meta object literal for the '<em><b>Work Item Id</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__WORK_ITEM_ID = eINSTANCE.getArchElement_WorkItemId();

		/**
		 * The meta object literal for the '<em><b>Rev</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ARCH_ELEMENT__REV = eINSTANCE.getArchElement_Rev();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.PartDefinitionImpl <em>Part Definition</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.PartDefinitionImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPartDefinition()
		 * @generated
		 */
		EClass PART_DEFINITION = eINSTANCE.getPartDefinition();

		/**
		 * The meta object literal for the '<em><b>Owned Parts</b></em>' containment reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART_DEFINITION__OWNED_PARTS = eINSTANCE.getPartDefinition_OwnedParts();

		/**
		 * The meta object literal for the '<em><b>Owned Ports</b></em>' containment reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART_DEFINITION__OWNED_PORTS = eINSTANCE.getPartDefinition_OwnedPorts();

		/**
		 * The meta object literal for the '<em><b>Super Types</b></em>' reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART_DEFINITION__SUPER_TYPES = eINSTANCE.getPartDefinition_SuperTypes();

		/**
		 * The meta object literal for the '<em><b>Is Abstract</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute PART_DEFINITION__IS_ABSTRACT = eINSTANCE.getPartDefinition_IsAbstract();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.PartImpl <em>Part</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.PartImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPart()
		 * @generated
		 */
		EClass PART = eINSTANCE.getPart();

		/**
		 * The meta object literal for the '<em><b>Owner</b></em>' container reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART__OWNER = eINSTANCE.getPart_Owner();

		/**
		 * The meta object literal for the '<em><b>Typed By</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART__TYPED_BY = eINSTANCE.getPart_TypedBy();

		/**
		 * The meta object literal for the '<em><b>Ports</b></em>' containment reference list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PART__PORTS = eINSTANCE.getPart_Ports();

		/**
		 * The meta object literal for the '<em><b>Multiplicity</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute PART__MULTIPLICITY = eINSTANCE.getPart_Multiplicity();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.PortImpl <em>Port</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.PortImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPort()
		 * @generated
		 */
		EClass PORT = eINSTANCE.getPort();

		/**
		 * The meta object literal for the '<em><b>Owner</b></em>' container reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PORT__OWNER = eINSTANCE.getPort_Owner();

		/**
		 * The meta object literal for the '<em><b>Part Owner</b></em>' container reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference PORT__PART_OWNER = eINSTANCE.getPort_PartOwner();

		/**
		 * The meta object literal for the '<em><b>Direction</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute PORT__DIRECTION = eINSTANCE.getPort_Direction();

		/**
		 * The meta object literal for the '<em><b>Interface Type</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute PORT__INTERFACE_TYPE = eINSTANCE.getPort_InterfaceType();

		/**
		 * The meta object literal for the '<em><b>Is Conjugated</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute PORT__IS_CONJUGATED = eINSTANCE.getPort_IsConjugated();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.ConnectionImpl <em>Connection</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.ConnectionImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getConnection()
		 * @generated
		 */
		EClass CONNECTION = eINSTANCE.getConnection();

		/**
		 * The meta object literal for the '<em><b>Source</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference CONNECTION__SOURCE = eINSTANCE.getConnection_Source();

		/**
		 * The meta object literal for the '<em><b>Target</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference CONNECTION__TARGET = eINSTANCE.getConnection_Target();

		/**
		 * The meta object literal for the '<em><b>Kind</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute CONNECTION__KIND = eINSTANCE.getConnection_Kind();

		/**
		 * The meta object literal for the '<em><b>Interface Type</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute CONNECTION__INTERFACE_TYPE = eINSTANCE.getConnection_InterfaceType();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.ItemFlowImpl <em>Item Flow</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.ItemFlowImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getItemFlow()
		 * @generated
		 */
		EClass ITEM_FLOW = eINSTANCE.getItemFlow();

		/**
		 * The meta object literal for the '<em><b>Connection</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference ITEM_FLOW__CONNECTION = eINSTANCE.getItemFlow_Connection();

		/**
		 * The meta object literal for the '<em><b>Item Name</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ITEM_FLOW__ITEM_NAME = eINSTANCE.getItemFlow_ItemName();

		/**
		 * The meta object literal for the '<em><b>Item Type</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ITEM_FLOW__ITEM_TYPE = eINSTANCE.getItemFlow_ItemType();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.RequirementImpl <em>Requirement</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.RequirementImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getRequirement()
		 * @generated
		 */
		EClass REQUIREMENT = eINSTANCE.getRequirement();

		/**
		 * The meta object literal for the '<em><b>Req Id</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute REQUIREMENT__REQ_ID = eINSTANCE.getRequirement_ReqId();

		/**
		 * The meta object literal for the '<em><b>Text</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute REQUIREMENT__TEXT = eINSTANCE.getRequirement_Text();

		/**
		 * The meta object literal for the '<em><b>Verify Method</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute REQUIREMENT__VERIFY_METHOD = eINSTANCE.getRequirement_VerifyMethod();

		/**
		 * The meta object literal for the '<em><b>Priority</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute REQUIREMENT__PRIORITY = eINSTANCE.getRequirement_Priority();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.AllocateImpl <em>Allocate</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.AllocateImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getAllocate()
		 * @generated
		 */
		EClass ALLOCATE = eINSTANCE.getAllocate();

		/**
		 * The meta object literal for the '<em><b>From</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference ALLOCATE__FROM = eINSTANCE.getAllocate_From();

		/**
		 * The meta object literal for the '<em><b>To</b></em>' reference feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EReference ALLOCATE__TO = eINSTANCE.getAllocate_To();

		/**
		 * The meta object literal for the '<em><b>Rationale</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute ALLOCATE__RATIONALE = eINSTANCE.getAllocate_Rationale();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.impl.DiagramLayoutImpl <em>Diagram Layout</em>}' class.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.impl.DiagramLayoutImpl
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getDiagramLayout()
		 * @generated
		 */
		EClass DIAGRAM_LAYOUT = eINSTANCE.getDiagramLayout();

		/**
		 * The meta object literal for the '<em><b>Diagram Key</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__DIAGRAM_KEY = eINSTANCE.getDiagramLayout_DiagramKey();

		/**
		 * The meta object literal for the '<em><b>Element Ref</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__ELEMENT_REF = eINSTANCE.getDiagramLayout_ElementRef();

		/**
		 * The meta object literal for the '<em><b>X</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__X = eINSTANCE.getDiagramLayout_X();

		/**
		 * The meta object literal for the '<em><b>Y</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__Y = eINSTANCE.getDiagramLayout_Y();

		/**
		 * The meta object literal for the '<em><b>Width</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__WIDTH = eINSTANCE.getDiagramLayout_Width();

		/**
		 * The meta object literal for the '<em><b>Height</b></em>' attribute feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__HEIGHT = eINSTANCE.getDiagramLayout_Height();

		/**
		 * The meta object literal for the '<em><b>Waypoints</b></em>' attribute list feature.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @generated
		 */
		EAttribute DIAGRAM_LAYOUT__WAYPOINTS = eINSTANCE.getDiagramLayout_Waypoints();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.Layer <em>Layer</em>}' enum.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.Layer
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getLayer()
		 * @generated
		 */
		EEnum LAYER = eINSTANCE.getLayer();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.PortDirection <em>Port Direction</em>}' enum.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.PortDirection
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getPortDirection()
		 * @generated
		 */
		EEnum PORT_DIRECTION = eINSTANCE.getPortDirection();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.ConnectionKind <em>Connection Kind</em>}' enum.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.ConnectionKind
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getConnectionKind()
		 * @generated
		 */
		EEnum CONNECTION_KIND = eINSTANCE.getConnectionKind();

		/**
		 * The meta object literal for the '{@link com.example.sysarch.model.VerifyMethod <em>Verify Method</em>}' enum.
		 * <!-- begin-user-doc -->
		 * <!-- end-user-doc -->
		 * @see com.example.sysarch.model.VerifyMethod
		 * @see com.example.sysarch.model.impl.SysarchPackageImpl#getVerifyMethod()
		 * @generated
		 */
		EEnum VERIFY_METHOD = eINSTANCE.getVerifyMethod();

	}

} //SysarchPackage
