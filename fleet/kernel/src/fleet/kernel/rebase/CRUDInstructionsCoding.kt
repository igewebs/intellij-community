// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.EidGen
import fleet.kernel.*
import fleet.util.UID
import kotlin.reflect.KClass

class LocalInstructionCoder<T: Instruction>(val c: KClass<T>): InstructionCoder<T, SharedInstruction> {
  override val instructionClass: KClass<T>
    get() = c
  override val sharedInstructionClass: KClass<SharedInstruction>?
    get() = null

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedInstruction): List<Instruction> {
    TODO("should not be called")
  }

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: T): SharedInstructionData =
    SharedInstructionData(null)
}

object CreateEntityCoder : InstructionCoder<CreateEntity, SharedCreateEntity> {
  override val instructionClass: KClass<CreateEntity> = CreateEntity::class
  override val sharedInstructionClass: KClass<SharedCreateEntity> = SharedCreateEntity::class

  private fun uidAndOtherAttributes(uidAttribute: Attribute<*>,
                                    attributes: List<Pair<Attribute<*>, Any>>): Pair<UID, List<Pair<Attribute<*>, Any>>>? =
    attributes.firstOrNull { (attr) -> attr == uidAttribute }?.let {
      it.second as UID to attributes.filter { (attr) -> attr != uidAttribute }
    }

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: CreateEntity): SharedInstructionData? =
    when (partition(instruction.eid)) {
      SharedPart, SchemaPart -> SharedInstructionData(
        uidAndOtherAttributes(uidAttribute(), instruction.attributes)?.let { (uid, attrs) ->
          SharedCreateEntity(
            entityId = uid,
            entityTypeIdent = entityTypeIdent(instruction.entityTypeEid)!!,
            attributes = attrs.map { (attr, value) ->
              val serializedValue = encodeDbValue(
                uidAttribute = serContext.uidAttribute,
                json = serContext.json,
                a = attr,
                v = value
              )
              val attribute = attributeIdent(attr)!!
              SharedCreateEntity.AttrValue(attr = attribute,
                                           schema = attr.schema.value,
                                           value = serializedValue)
            },
            seed = instruction.seed
          )
        })
      else -> null
    }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedCreateEntity): List<Instruction> =
    buildList {
      // don't forget to register all attributes with their ident
      val entityEID = EidGen.freshEID(SharedPart)

      val attrValues = sharedInstruction.attributes.map { (attr, schema, dbValue) ->
        val attribute = attributeByIdent(attr) ?: run {
          val createEntity = createUnknownAttribute(attr, Schema(schema), sharedInstruction.seed)
          add(createEntity)
          Attribute<Any>(createEntity.eid)
        }
        val value = deserializeValue(dbValue, deserContext, attribute)
        attribute to value
      } + (deserContext.uidAttribute to sharedInstruction.entityId)

      val entityTypeEid = run {
        val createEntityType = createUnknownEntityType(
          ident = sharedInstruction.entityTypeIdent,
          attrs = attrValues.map { it.first }.toSet(),
          seed = sharedInstruction.seed
        )
        add(createEntityType)
        createEntityType.eid
      }

      add(CreateEntity(eid = entityEID,
                       entityTypeEid = entityTypeEid,
                       attributes = attrValues,
                       seed = sharedInstruction.seed))
    }
}

object CompositeCoder : InstructionCoder<AtomicComposite, SharedAtomicComposite> {
  override val instructionClass: KClass<AtomicComposite>
    get() = AtomicComposite::class
  override val sharedInstructionClass: KClass<SharedAtomicComposite>
    get() = SharedAtomicComposite::class

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedAtomicComposite): List<Instruction> =
    listOf(AtomicComposite(
      instructions = sharedInstruction.instructions.flatMap {
        deserContext.decoder.run { decode(deserContext, it) }
      },
      seed = sharedInstruction.seed))

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: AtomicComposite): SharedInstructionData? =
    instruction.instructions
      .mapNotNull { serContext.encoder.run { encode(serContext, it) } }
      .takeIf { it.isNotEmpty() }
      ?.let { datas ->
        SharedInstructionData(SharedAtomicComposite(
          instructions = datas.mapNotNull { it.sharedInstruction },
          seed = instruction.seed)
        )
      }
}

object AddCoder : InstructionCoder<Add<*>, SharedAdd> {
  override val instructionClass: KClass<Add<*>> = Add::class
  override val sharedInstructionClass: KClass<SharedAdd> = SharedAdd::class

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: Add<*>): SharedInstructionData? =
    when (partition(instruction.eid)) {
      SharedPart, SchemaPart ->
        SharedInstructionData(sharedId(instruction.eid, serContext.uidAttribute)?.let { uid ->
          SharedAdd(
            entityId = uid,
            attribute = attributeIdent(instruction.attribute)!!,
            schema = instruction.attribute.schema.value,
            value = encodeDbValue(
              uidAttribute = serContext.uidAttribute,
              json = serContext.json,
              a = instruction.attribute,
              v = instruction.value
            ),
            seed = instruction.seed)
        })
      else -> null
    }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedAdd): List<Instruction> =
    buildList {
      val eid = lookupSingle(deserContext.uidAttribute, sharedInstruction.entityId)
      val attribute = attributeByIdent(sharedInstruction.attribute) ?: run {
        val createAttribute = createUnknownAttribute(sharedInstruction.attribute, Schema(sharedInstruction.schema), sharedInstruction.seed)
        add(createAttribute)
        Attribute<Any>(createAttribute.eid)
      }
      val entityTypeEid = entityType(eid)!!
      add(Add(eid = entityTypeEid,
              attribute = EntityType.PossibleAttributes.attr as Attribute<EID>,
              value = attribute.eid,
              seed = sharedInstruction.seed))
      add(Add(eid = eid,
              attribute = attribute as Attribute<Any>,
              value = deserializeValue(sharedInstruction.value, deserContext, attribute),
              seed = sharedInstruction.seed))
    }
}

object RemoveCoder : InstructionCoder<Remove<*>, SharedRemove> {
  override val instructionClass: KClass<Remove<*>> = Remove::class
  override val sharedInstructionClass: KClass<SharedRemove> = SharedRemove::class

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: Remove<*>): SharedInstructionData? =
    when (partition(instruction.eid)) {
      SharedPart, SchemaPart ->
        SharedInstructionData(sharedId(instruction.eid, serContext.uidAttribute)?.let { uid ->
          val attribute = attributeIdent(instruction.attribute)!!
          val value = encodeDbValue(
            uidAttribute = serContext.uidAttribute,
            json = serContext.json,
            a = instruction.attribute,
            v = instruction.value
          )
          SharedRemove(entityId = uid,
                       attribute = attribute,
                       value = value,
                       seed = instruction.seed)
        })
      else -> null
    }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedRemove): List<Remove<*>> {
    val eid = lookupSingle(deserContext.uidAttribute, sharedInstruction.entityId)
    val attribute = requireAttributeByIdent(sharedInstruction.attribute)
    return listOf(Remove(eid = eid,
                         attribute = attribute,
                         value = deserializeValue(sharedInstruction.value, deserContext, attribute),
                         seed = sharedInstruction.seed))
  }
}

private fun DbContext<Q>.deserializeValue(value: DurableDbValue,
                                          deserContext: InstructionDecodingContext,
                                          attribute: Attribute<*>) = when (value) {
  is DurableDbValue.EntityRef -> lookupSingle(deserContext.uidAttribute, value.entityId)
  is DurableDbValue.EntityTypeRef -> requireNotNull(entityTypeByIdent(value.ident)) {
    "entity type ${value.ident} not found"
  }
  is DurableDbValue.Scalar -> deserialize(attribute, value.json, deserContext.serialization)
}

object RetractAttributeCoder : InstructionCoder<RetractAttribute, SharedRetractAttribute> {
  override val instructionClass: KClass<RetractAttribute> = RetractAttribute::class
  override val sharedInstructionClass: KClass<SharedRetractAttribute> = SharedRetractAttribute::class

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: RetractAttribute): SharedInstructionData? =
    when (partition(instruction.eid)) {
      SharedPart, SchemaPart ->
        SharedInstructionData(sharedId(instruction.eid, serContext.uidAttribute)?.let { uid ->
          val attribute = attributeIdent(instruction.attribute)!!
          SharedRetractAttribute(entityId = uid,
                                 attribute = attribute,
                                 seed = instruction.seed)
        })
      else -> null
    }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedRetractAttribute): List<Instruction> =
    lookupOne(deserContext.uidAttribute, sharedInstruction.entityId)?.let { eid ->
      attributeByIdent(sharedInstruction.attribute)?.let { attribute ->
        // attribute missing in schema means that no data was transacted for it, nothing to retract then:
        listOf(RetractAttribute(eid = eid,
                                attribute = attribute,
                                seed = sharedInstruction.seed))
      }
    } ?: emptyList()
}

object RetractEntityCoder : InstructionCoder<RetractEntityInPartition, SharedRetractEntity> {
  override val instructionClass: KClass<RetractEntityInPartition> = RetractEntityInPartition::class
  override val sharedInstructionClass: KClass<SharedRetractEntity> = SharedRetractEntity::class

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: RetractEntityInPartition): SharedInstructionData? =
    when (partition(instruction.eid)) {
      SharedPart, SchemaPart ->
        SharedInstructionData(getOne(instruction.eid, serContext.uidAttribute)?.let { uid ->
          SharedRetractEntity(entityId = uid,
                              seed = instruction.seed)
        })
      else -> null
    }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedRetractEntity): List<Instruction> =
    lookupOne(deserContext.uidAttribute, sharedInstruction.entityId)?.let { eid ->
      listOf(AtomicComposite(
        instructions = impl.entitiesToRetract(eid).map {
          RetractEntityInPartition(it, sharedInstruction.seed)
        },
        seed = sharedInstruction.seed
      ))
    } ?: emptyList()
}

object ValidateCoder : InstructionCoder<Validate, SharedValidate> {
  override val instructionClass: KClass<Validate> = Validate::class
  override val sharedInstructionClass: KClass<SharedValidate> = SharedValidate::class

  override fun DbContext<Q>.encode(serContext: InstructionEncodingContext, instruction: Validate): SharedInstructionData? {
    throw IllegalArgumentException("should not be called")
  }

  override fun DbContext<Q>.decode(deserContext: InstructionDecodingContext, sharedInstruction: SharedValidate): List<Validate> =
    listOf(Validate(indexQuery = decodeQuery(query = sharedInstruction.q,
                                             json = deserContext.serialization,
                                             uidAttribute = deserContext.uidAttribute),
                    trace = sharedInstruction.trace))
}
