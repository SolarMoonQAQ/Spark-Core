package au.edu.federation.caliko;

import au.edu.federation.caliko.FabrikChain3D.BaseboneConstraintType3D;
import au.edu.federation.utils.Utils;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FabrikStructure3D implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String NEW_LINE = System.lineSeparator();

    private String mName;

    private List<FabrikChain3D> mChains = new ArrayList<>();

    public FabrikStructure3D() {
    }

    public FabrikStructure3D(String name) {
        mName = Utils.getValidatedName(name);
    }

    public void solveForTarget(Vector3f newTargetLocation) {
        int numChains = mChains.size();
        int connectedChainNumber;

        for (int loop = 0; loop < numChains; ++loop) {
            FabrikChain3D thisChain = mChains.get(loop);
            connectedChainNumber = thisChain.getConnectedChainNumber();

            if (connectedChainNumber == -1) {
                thisChain.solveForTarget(newTargetLocation);
            } else {
                FabrikChain3D hostChain = mChains.get(connectedChainNumber);
                FabrikBone3D hostBone = hostChain.getBone(thisChain.getConnectedBoneNumber());
                if (hostBone.getBoneConnectionPoint() == BoneConnectionPoint.START) {
                    thisChain.setBaseLocation(hostBone.getStartLocation());
                } else {
                    thisChain.setBaseLocation(hostBone.getEndLocation());
                }

                BaseboneConstraintType3D constraintType = thisChain.getBaseboneConstraintType();
                switch (constraintType) {
                    case NONE:
                    case GLOBAL_ROTOR:
                    case GLOBAL_HINGE:
                        break;

                    case LOCAL_ROTOR:
                    case LOCAL_HINGE: {
                        Matrix3f connectionBoneMatrix = Utils.createRotationMatrix(hostBone.getDirectionUV(), new Matrix3f());

                        Vector3f relativeBaseboneConstraintUV = connectionBoneMatrix.transform(thisChain.getBaseboneConstraintUV(), new Vector3f()).normalize(new Vector3f());

                        thisChain.setBaseboneRelativeConstraintUV(relativeBaseboneConstraintUV);

                        if (constraintType == BaseboneConstraintType3D.LOCAL_HINGE) {
                            thisChain.setBaseboneRelativeReferenceConstraintUV(connectionBoneMatrix.transform(thisChain.getBone(0).getJoint().getHingeReferenceAxis(), new Vector3f()));
                        }
                        break;
                    }
                }

                if (!thisChain.getEmbeddedTargetMode()) {
                    thisChain.solveForTarget(newTargetLocation);
                } else {
                    thisChain.solveForEmbeddedTarget();
                }

            }

        }

    }

    public void addChain(FabrikChain3D chain) {
        mChains.add(chain);
    }

    public void removeChain(int chainIndex) {
        mChains.remove(chainIndex);
    }

    public void connectChain(FabrikChain3D newChain, int existingChainNumber, int existingBoneNumber) {
        if (existingChainNumber > this.mChains.size()) {
            throw new IllegalArgumentException("Cannot connect to chain " + existingChainNumber + " - no such chain (remember that chains are zero indexed).");
        }

        if (existingBoneNumber > mChains.get(existingChainNumber).getNumBones()) {
            throw new IllegalArgumentException("Cannot connect to bone " + existingBoneNumber + " of chain " + existingChainNumber + " - no such bone (remember that bones are zero indexed).");
        }

        FabrikChain3D relativeChain = new FabrikChain3D(newChain);

        relativeChain.connectToStructure(this, existingChainNumber, existingBoneNumber);

        BoneConnectionPoint connectionPoint = this.getChain(existingChainNumber).getBone(existingBoneNumber).getBoneConnectionPoint();
        Vector3f connectionLocation;
        if (connectionPoint == BoneConnectionPoint.START) {
            connectionLocation = mChains.get(existingChainNumber).getBone(existingBoneNumber).getStartLocation();
        } else {
            connectionLocation = mChains.get(existingChainNumber).getBone(existingBoneNumber).getEndLocation();
        }
        relativeChain.setBaseLocation(connectionLocation);

        relativeChain.setFixedBaseMode(true);

        for (int loop = 0; loop < relativeChain.getNumBones(); ++loop) {
            Vector3f origStart = relativeChain.getBone(loop).getStartLocation();
            Vector3f origEnd = relativeChain.getBone(loop).getEndLocation();

            Vector3f translatedStart = origStart.add(connectionLocation, new Vector3f());
            Vector3f translatedEnd = origEnd.add(connectionLocation, new Vector3f());

            relativeChain.getBone(loop).setStartLocation(translatedStart);
            relativeChain.getBone(loop).setEndLocation(translatedEnd);
        }

        this.addChain(relativeChain);
    }

    public void connectChain(FabrikChain3D newChain, int existingChainNumber, int existingBoneNumber, BoneConnectionPoint boneConnectionPoint) {
        if (existingChainNumber > this.mChains.size()) {
            throw new IllegalArgumentException("Cannot connect to chain " + existingChainNumber + " - no such chain (remember that chains are zero indexed).");
        }

        if (existingBoneNumber > mChains.get(existingChainNumber).getNumBones()) {
            throw new IllegalArgumentException("Cannot connect to bone " + existingBoneNumber + " of chain " + existingChainNumber + " - no such bone (remember that bones are zero indexed).");
        }

        FabrikChain3D relativeChain = new FabrikChain3D(newChain);

        relativeChain.connectToStructure(this, existingChainNumber, existingBoneNumber);

        this.getChain(existingChainNumber).getBone(existingBoneNumber).setBoneConnectionPoint(boneConnectionPoint);
        Vector3f connectionLocation;
        if (boneConnectionPoint == BoneConnectionPoint.START) {
            connectionLocation = mChains.get(existingChainNumber).getBone(existingBoneNumber).getStartLocation();
        } else {
            connectionLocation = mChains.get(existingChainNumber).getBone(existingBoneNumber).getEndLocation();
        }
        relativeChain.setBaseLocation(connectionLocation);

        relativeChain.setFixedBaseMode(true);

        for (int loop = 0; loop < relativeChain.getNumBones(); ++loop) {
            Vector3f origStart = relativeChain.getBone(loop).getStartLocation();
            Vector3f origEnd = relativeChain.getBone(loop).getEndLocation();

            Vector3f translatedStart = origStart.add(connectionLocation, new Vector3f());
            Vector3f translatedEnd = origEnd.add(connectionLocation, new Vector3f());

            relativeChain.getBone(loop).setStartLocation(translatedStart);
            relativeChain.getBone(loop).setEndLocation(translatedEnd);
        }

        this.addChain(relativeChain);
    }

    public int getNumChains() {
        return this.mChains.size();
    }

    public FabrikChain3D getChain(int chainNumber) {
        return mChains.get(chainNumber);
    }

    public void setFixedBaseMode(boolean fixedBaseMode) {
        for (int loop = 0; loop < this.mChains.size(); ++loop) {
            mChains.get(loop).setFixedBaseMode(fixedBaseMode);
        }
    }

    public void setName(String name) {
        mName = Utils.getValidatedName(name);
    }

    public String getName() {
        return this.mName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("----- FabrikStructure3D: " + mName + " -----" + NEW_LINE);

        sb.append("Number of chains: " + this.mChains.size() + NEW_LINE);

        for (int loop = 0; loop < this.mChains.size(); ++loop) {
            sb.append(mChains.get(loop).toString());
        }

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mChains == null) ? 0 : mChains.hashCode());
        result = prime * result + ((mName == null) ? 0 : mName.hashCode());
        return result;
    }
}