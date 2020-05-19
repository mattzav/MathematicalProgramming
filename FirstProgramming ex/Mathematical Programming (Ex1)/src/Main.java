
/* --------------------------------------------------------------------------
* File: MIPex1.java
* Version 12.8.0
* --------------------------------------------------------------------------
* Licensed Materials - Property of IBM
* 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
* Copyright IBM Corporation 2001, 2017. All Rights Reserved.
*
* US Government Users Restricted Rights - Use, duplication or
* disclosure restricted by GSA ADP Schedule Contract with
* IBM Corp.
* --------------------------------------------------------------------------
*
* MIPex1.java - Entering and optimizing a MIP problem
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import ilog.concert.*;
import ilog.concert.IloCopyManager.Check;
import ilog.cplex.*;
import ilog.cplex.IloCplex.Callback;
import ilog.cplex.IloCplex.Goal;
import ilog.cplex.IloCplex.IncumbentCallback;

import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;

// 1) number of nodes
// 2) number of edges
// 3) index, node1, node2, weight
public class Main {

	static int numberOfNodes;
	static int numberOfEdges;
	static Pair[] edges;

	static int[] weights;

	static int k;

	public static void main(String[] args) throws IOException, IloException {

		for (int i = 1; i <= 10; i++) {

			readInstance(i);
			System.out.println("Instance " + i);
			for (int iter = 0; iter <= 1; iter++) {
				if (iter == 1)
					k = numberOfNodes / 2;
				else
					k = numberOfNodes / 5;

				System.out.println("k = " + k);
				for (int modelIter = 0; modelIter <= 2; modelIter++) {
					IloCplex model = new IloCplex();
					if (modelIter == 0)
						createMtzModel(model);
					else if (modelIter == 1)
						createSCFModel(model);
					else
						createMCFModel(model);

					long start = System.currentTimeMillis();
					if (model.solve()) {
						System.out.println(getNameModel(modelIter) + " Time="
								+ (System.currentTimeMillis() - start) / 1000 + " Gap=" + model.getMIPRelativeGap()
								+ " Number of Nodes = " + model.getNnodes() + " Obj Value=" + model.getObjValue());
					} else {
						System.out.println(getNameModel(modelIter) + " Time="
								+ (System.currentTimeMillis() - start) / 1000 + " Gap=" + model.getMIPRelativeGap()
								+ " Number of Nodes = " + model.getNnodes() + " Obj Value=" + model.getObjValue());
					}
				}
				System.out.println();
			}

		}

	}

	private static String getNameModel(int model) {
		if (model == 0)
			return "MTZ ";
		if (model == 1)
			return "SCF ";
		return "MCF ";
	}

	private static void createMtzModel(IloCplex model) throws IloException {

		System.out.println("Loading MTZ..");
		int bigM = k;

		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));

		IloNumVar[] y = model.boolVarArray(numberOfNodes);
		IloNumVar[] u = model.numVarArray(numberOfNodes, 0.0, k);

		model.addMinimize(model.scalProd(x, weights)); // objective function (1)

		model.addEq(model.sum(y), k + 1); // constraint (2)

		model.addEq(model.sum(x), k); // constraint (3)

		// constraint (4)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneIncomingForNonRoot = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {

				if (edges[index].getEndpoint_2() == node)
					oneIncomingForNonRoot.addTerm(1, x[index]);
				else if (edges[index].getEndpoint_1() == node)
					oneIncomingForNonRoot.addTerm(1, x[index + numberOfEdges - (numberOfNodes - 1)]);
			}
			model.addEq(oneIncomingForNonRoot, y[node]);
		}
		// end constraint (4)

		// constraint (5)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (5)

		// constraint (6)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0 && endPoint_2 != 0) {
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]),
						model.prod(2, model.sum(x[index], x[index + numberOfEdges - (numberOfNodes - 1)])));
			}
		}
		// end constraint (6)

		// constraint (7)
		for (int index = 1; index < numberOfNodes; index++) {
			model.addLe(u[index], model.prod(y[index], k));
			model.addGe(u[index], y[index]);
		}
		// end constraint (7)

		// constraint (8)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0 && endPoint_2 != 0) {
				model.addLe(model.sum(u[endPoint_1], x[index]),
						model.sum(u[endPoint_2], model.prod(bigM, model.sum(1, model.prod(-1, x[index])))));

				model.addLe(model.sum(u[endPoint_2], x[index + numberOfEdges - (numberOfNodes - 1)]),
						model.sum(u[endPoint_1], model.prod(bigM,
								model.sum(1, model.prod(-1, x[index + numberOfEdges - (numberOfNodes - 1)])))));
			}
		}
		// end constraint (8)

		model.addEq(y[0], 1); // constraint (9)
		model.addEq(u[0], 0); // constraint (10)

	}

	private static void createSCFModel(IloCplex model) throws IloException {

		System.out.println("Loading SCF..");
		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));
		IloNumVar[] f = model.numVarArray(numberOfEdges * 2 - (numberOfNodes - 1), 0.0, k);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

		model.addMinimize(model.scalProd(x, weights)); // objective function (13)

		model.addEq(model.sum(y), k + 1); // constraint (14)

		model.addEq(model.sum(x), k); // constraint (15)

		// constraint (16)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneIncomingForNonRoot = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {
				if (edges[index].getEndpoint_2() == node)
					oneIncomingForNonRoot.addTerm(1, x[index]);
				else if (edges[index].getEndpoint_1() == node)
					oneIncomingForNonRoot.addTerm(1, x[index + numberOfEdges - (numberOfNodes - 1)]);
			}
			model.addEq(oneIncomingForNonRoot, y[node]);
		}
		// end constraint (16)

		// constraint (17)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (17)

		// constraint (18)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0 && endPoint_2 != 0) {
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]),
						model.prod(2, model.sum(x[index], x[index + numberOfEdges - (numberOfNodes - 1)])));
			}
		}
		// end constraint (18)

		// constraint (19)
		IloLinearNumExpr kExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				kExitingByZero.addTerm(1, f[index]);
		model.addEq(kExitingByZero, k);
		// end constraint (19)

		// constraint (20)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneFlowMantained = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {
				if (edges[index].getEndpoint_2() == node) {
					oneFlowMantained.addTerm(1, f[index]);
					if (edges[index].getEndpoint_1() != 0)
						oneFlowMantained.addTerm(-1, f[index + numberOfEdges - (numberOfNodes - 1)]);
				} else if (edges[index].getEndpoint_1() == node) {
					oneFlowMantained.addTerm(1, f[index + numberOfEdges - (numberOfNodes - 1)]);
					oneFlowMantained.addTerm(-1, f[index]);
				}
			}
			model.addEq(oneFlowMantained, y[node]);
		}
		// end constraint (20)

		// constraint(21)
		for (int index = 0; index < numberOfEdges; index++) {
			model.addLe(f[index], model.prod(k, x[index]));
			model.addLe(f[index + numberOfEdges - (numberOfNodes - 1)],
					model.prod(k, x[index + numberOfEdges - (numberOfNodes - 1)]));

		}
		// end constraint(21)

		model.addEq(y[0], 1); // constraint (22)

	}

	private static void createMCFModel(IloCplex model) throws IloException {

		System.out.println("Loading MCF..");
		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));

		IloNumVar[][] f = new IloNumVar[numberOfEdges * 2 - (numberOfNodes - 1)][];
		for (int index = 0; index < numberOfEdges * 2 - (numberOfNodes - 1); index++)
			f[index] = model.numVarArray(k, 0, 1);

		

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

		model.addMinimize(model.scalProd(x, weights)); // objective function (25)

		model.addEq(model.sum(y), k + 1); // constraint (26)

		model.addEq(model.sum(x), k); // constraint (27)

		// constraint (28)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneIncomingForNonRoot = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {
				if (edges[index].getEndpoint_2() == node)
					oneIncomingForNonRoot.addTerm(1, x[index]);
				else if (edges[index].getEndpoint_1() == node)
					oneIncomingForNonRoot.addTerm(1, x[index + numberOfEdges - (numberOfNodes - 1)]);
			}
			model.addEq(oneIncomingForNonRoot, y[node]);
		}
		// end constraint (28)

		// constraint (29)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (29)

		// constraint (30)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0)
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.sum(model.prod(2, x[index]),
						model.prod(2, x[index + numberOfEdges - (numberOfNodes - 1)])));

		}
		// end constraint (30)

		// constraint (31)
		for (int commodity = 1; commodity < numberOfNodes; commodity++) {
			for (int node = 0; node < numberOfNodes; node++) {
				// compute produced flow for each node and for each commodity
				IloLinearNumExpr mantainedFlowFromIndexCommmodityI = model.linearNumExpr();
				for (int arc = 0; arc < numberOfEdges; arc++) {
					if (edges[arc].getEndpoint_2() == node) {
						mantainedFlowFromIndexCommmodityI.addTerm(-1, f[arc][commodity]);

						if (edges[arc].getEndpoint_1() != 0)
							mantainedFlowFromIndexCommmodityI.addTerm(1,
									f[arc + numberOfEdges - (numberOfNodes - 1)][commodity]);
					} else if (edges[arc].getEndpoint_1() == node) {
						mantainedFlowFromIndexCommmodityI.addTerm(1, f[arc][commodity]);

						if (edges[arc].getEndpoint_1() != 0)
							mantainedFlowFromIndexCommmodityI.addTerm(-1,
									f[arc + numberOfEdges - (numberOfNodes - 1)][commodity]);
					}
				}

				if (node == 0)
					model.addEq(mantainedFlowFromIndexCommmodityI, y[commodity]);
				else if (node == commodity)
					model.addEq(mantainedFlowFromIndexCommmodityI, model.prod(-1, y[node]));
				else
					model.addEq(mantainedFlowFromIndexCommmodityI, 0);

			}
		}

		// end constraint (31)

		// constraint (32)
		for (int commodity = 0; commodity < numberOfNodes; commodity++) {
			for (int arc = 0; arc < numberOfEdges; arc++) {
				model.addLe(f[arc][commodity], x[arc]);
				if (edges[arc].getEndpoint_1() != 0)
					model.addLe(f[arc + numberOfEdges - (numberOfNodes - 1)][commodity],
							x[arc + numberOfEdges - (numberOfNodes - 1)]);

			}
		}
		// end constraint (32)

		model.addEq(y[0], 1); // constraint (33)

	}

	private static void readInstance(int i) throws NumberFormatException, IOException {
		String file = "";

		if (i <= 9)
			file = "data/g0" + i + ".dat";
		else
			file = "data/g10.dat";

		// file = "data/prova.dat";

		BufferedReader br = new BufferedReader(new FileReader(file));

		numberOfNodes = Integer.valueOf(br.readLine());
		numberOfEdges = Integer.valueOf(br.readLine());
		edges = new Pair[numberOfEdges];

		// {(i,j),(j,i)}-{(v,0)} so numberOfEdges*2 - (numberOfNodes-1)
		// This will be the cardinality of each sets of decision variables for the arcs!
		// Note that, since I don't include the arcs incoming in 0, you will find a lot
		// of (if endPoint1 != 0) since for these arcs (0,j) we will not have the
		// corresponding decision variables for the arc (j,0)
		weights = new int[numberOfEdges * 2 - (numberOfNodes - 1)];

		String currentRow;
		for (int rangeEdge = 0; rangeEdge < numberOfEdges; rangeEdge++) {
			currentRow = br.readLine();
			String parameters[] = currentRow.split(" ");
			int indexOfEdge = Integer.valueOf(parameters[0]);
			int endPoint_1 = Integer.valueOf(parameters[1]);
			int endPoint_2 = Integer.valueOf(parameters[2]);
			int weight = Integer.valueOf(parameters[3]);

			edges[indexOfEdge] = new Pair(endPoint_1, endPoint_2);

			// note that for the arc {i,j} with index k, I add w_{i,j} in position k and
			// in position k + numberOfEdges -(numberofNodes-1).
			// This situation will be reflected for all variables referring to the arcs:
			// In particular if we have an arc {i,j} with index k: x[k] -> (i,j) and
			// x[k+numberOfEdges - (numberOfNodes-1)] -> (j,i)

			weights[indexOfEdge] = weight;
			if (endPoint_1 != 0)
				weights[indexOfEdge + numberOfEdges - (numberOfNodes - 1)] = weight;

		}

	}
}