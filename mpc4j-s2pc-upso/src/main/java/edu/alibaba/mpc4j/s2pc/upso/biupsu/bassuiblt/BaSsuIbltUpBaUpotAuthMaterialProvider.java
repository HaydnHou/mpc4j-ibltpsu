package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Party-local provider for UP-BA-UPOT authentication material.
 *
 * <p>The implementation must derive the returned bytes from secret local material, such as SS-OTag / OPRF output or
 * UP-BA-UPOT offline material. Public probe data and the local layer role are domain-separation inputs only; they are
 * not sufficient to generate authentication material by themselves.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
interface BaSsuIbltUpBaUpotAuthMaterialProvider {
    /**
     * Returns authentication material for one party-local public probe.
     *
     * @param publicInput public probe input.
     * @param ownLayer    this party's local layer role.
     * @param ownCellView this party's local cell view.
     * @return authentication material.
     */
    byte[] authMaterial(BaSsuIbltUpBaUpotPublicInput publicInput,
                        BaSsuIbltProductionUnionProbeLocalLayer ownLayer,
                        BaSsuIbltSecureCellView ownCellView);
}
