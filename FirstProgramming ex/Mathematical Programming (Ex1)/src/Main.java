
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

			for (int iter = 0; iter <= 1; iter++) {
				if (iter == 1)
					k = numberOfNodes / 2;
				else
					k = numberOfNodes / 5;

				for (int a = 1; a <= 1; a++) {
					IloCplex model = new IloCplex();
					if (a == 1)
						createMCFModel(model);

					long start = System.currentTimeMillis();
					// double startC = model.getCplexTime();
					if (model.solve()) {
						long elapsed = (System.currentTimeMillis() - start) / 1000;
//					double elapsedC = model.getCplexTime() - startC;
						System.out.print(model.getObjValue() + " " + elapsed + " --- ");
//
//					for (int index = 0; index < numberOfNodes; index++) {
//
//						if (model.getValue(y[index]) != 0)
//							System.out.println(y[index].getName() + " " + model.getValue(y[index]));
//					}
//
//					for (int index = 0; index < numberOfEdges; index++) {
//						if (model.getValue(x[index]) != 0)
//							System.out.println(x[index].getName() + " " + model.getValue(x[index]));
//						if (model.getValue(v[index]) != 0)
//							System.out.println(v[index].getName() + " " + model.getValue(v[index]));
//					}
					}
				}
				System.out.println();
			}

		}

	}

	private static void createMCFModel(IloCplex model) throws IloException {

		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));

		IloNumVar[][] f = new IloNumVar[numberOfEdges * 2 - (numberOfNodes - 1)][];
		for (int index = 0; index < numberOfEdges * 2 - (numberOfNodes - 1); index++)
			f[index] = model.numVarArray(numberOfNodes, 0, 1);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

		for (int index = 0; index < numberOfNodes; index++) {
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");

			for (int i = 0; i < numberOfNodes; i++) {
				f[index][i].setName(
						"f_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "," + (i) + "}");

			}

		}

		// creating objective function
		model.addMinimize(model.scalProd(x, weights));

		model.addEq(model.sum(y), k + 1); // constraint (1)

		model.addEq(model.sum(x), k); // constraint (2)

		// constraint (3)
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
		// end constraint (3)

		// constraint (4)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (4)

		// constraint (5)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0)
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.sum(model.prod(2, x[index]),
						model.prod(2, x[index + numberOfEdges - (numberOfNodes - 1)])));
			else
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.prod(2, x[index]));

		}
		// end constraint (5)

		// constraint (6)
		for (int i = 0; i < numberOfNodes; i++) {
			for (int index = 0; index < numberOfNodes; index++) {
				// compute mantained flow for node index for commodity i
				IloLinearNumExpr mantainedFlowFromIndexCommmodityI = model.linearNumExpr();
				for (int j = 0; j < numberOfEdges; j++) {
					if (edges[j].getEndpoint_2() == index) {
						mantainedFlowFromIndexCommmodityI.addTerm(1, f[j][i]);

						if (edges[j].getEndpoint_1() != 0)
							mantainedFlowFromIndexCommmodityI.addTerm(-1,
									f[j + numberOfEdges - (numberOfNodes - 1)][i]);
					} else if (edges[j].getEndpoint_1() == index) {
						mantainedFlowFromIndexCommmodityI.addTerm(-1, f[j][i]);

						if (edges[j].getEndpoint_1() != 0)
							mantainedFlowFromIndexCommmodityI.addTerm(1, f[j + numberOfEdges - (numberOfNodes - 1)][i]);
					}
				}

				if (index == 0 && i != 0)
					model.addEq(mantainedFlowFromIndexCommmodityI, model.prod(-1, y[i]));
				else if (index == i && i != 0)
					model.addEq(mantainedFlowFromIndexCommmodityI, y[index]);
				else
					model.addEq(mantainedFlowFromIndexCommmodityI, 0);

			}
		}

		// end constraint (6)

		// constraint (7)
		for (int i = 0; i < numberOfNodes; i++) {
			for (int index = 0; index < numberOfEdges; index++) {
				model.addLe(f[index][i], model.prod(1, x[index]));
				if (edges[index].getEndpoint_1() != 0)
					model.addLe(f[index + numberOfEdges - (numberOfNodes - 1)][i],
							model.prod(1, x[index + numberOfEdges - (numberOfNodes - 1)]));

			}
		}
		// end constraint (7)

		model.addEq(y[0], 1); // constraint (9)

	}

	private static void createSCFModel(IloCplex model) throws IloException {

		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));
		IloNumVar[] f = model.numVarArray(numberOfEdges * 2 - (numberOfNodes - 1), 0.0, k);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

		for (int index = 0; index < numberOfNodes; index++) {
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			x[index + numberOfEdges - (numberOfNodes - 1)]
					.setName("x_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");

			f[index].setName("f_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			f[index + numberOfEdges - (numberOfNodes - 1)]
					.setName("f_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");

		}

		// creating objective function
		model.addMinimize(model.scalProd(x, weights));

		model.addEq(model.sum(y), k + 1); // constraint (1)

		model.addEq(model.sum(x), k); // constraint (2)

		// constraint (3)
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
		// end constraint (3)

		// constraint (4)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (4)

		// constraint (5)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0)
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.sum(model.prod(2, x[index]),
						model.prod(2, x[index + numberOfEdges - (numberOfNodes - 1)])));
			else
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.prod(2, x[index]));

		}
		// end constraint (5)

		// constraint (6)
		IloLinearNumExpr kExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				kExitingByZero.addTerm(1, f[index]);
		model.addEq(kExitingByZero, k);
		// end constraint (6)

		// constraint (7)
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
		// end constraint (7)

		// constraint(8)
		for (int index = 0; index < numberOfEdges; index++) {
			model.addLe(f[index], model.prod(k, x[index]));
			model.addLe(f[index + numberOfEdges - (numberOfNodes - 1)],
					model.prod(k, x[index + numberOfEdges - (numberOfNodes - 1)]));

		}
		// end constraint(8)

		model.addEq(y[0], 1); // constraint (9)

	}

	private static void createMtzModelModified(IloCplex model) throws IloException {

		int bigM = k - 1;

		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges * 2 - (numberOfNodes - 1));

		IloNumVar[] y = model.boolVarArray(numberOfNodes);
		IloNumVar[] u = model.numVarArray(numberOfNodes, 0.0, k);

		for (int index = 0; index < numberOfNodes; index++) {
			u[index].setName("u" + index);
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");

		}

		// creating objective function
		model.addMinimize(model.scalProd(x, weights));

		model.addEq(model.sum(y), k + 1); // constraint (1)

		model.addEq(model.sum(x), k); // constraint (2)

		// constraint (3)
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
		// end constraint (3)

		// constraint (4)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (4)

		// constraint (5)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			if (endPoint_1 != 0)
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.sum(model.prod(2, x[index]),
						model.prod(2, x[index + numberOfEdges - (numberOfNodes - 1)])));
			else
				model.addGe(model.sum(y[endPoint_1], y[endPoint_2]), model.prod(2, x[index]));

		}
		// end constraint (5)

		// constraint (6)
		for (int index = 1; index < numberOfNodes; index++) {
			model.addLe(u[index], model.prod(y[index], k));
			model.addGe(u[index], y[index]);
		}
		// end constraint (6)

		// constraint (7)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			model.addLe(model.sum(u[endPoint_1], x[index]),
					model.sum(u[endPoint_2], model.prod(bigM, model.sum(1, model.prod(-1, x[index])))));

			if (endPoint_1 != 0)
				model.addLe(model.sum(u[endPoint_2], x[index + numberOfEdges - (numberOfNodes - 1)]),
						model.sum(u[endPoint_1], model.prod(bigM,
								model.sum(1, model.prod(-1, x[index + numberOfEdges - (numberOfNodes - 1)])))));
		}
		// end constraint (7)

		model.addEq(u[0], 0); // constraint (8)

		model.addEq(y[0], 1); // constraint (9)

	}

	private static void createMtzModel(int iter, IloCplex model) throws IloException {

		int bigM = k - 1;

		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges);
		IloNumVar[] v = model.boolVarArray(numberOfEdges);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);
		IloNumVar[] u = model.numVarArray(numberOfNodes, 0.0, k);

		for (int index = 0; index < numberOfNodes; index++) {
			u[index].setName("u" + index);
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			v[index].setName("x_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");
		}

		// creating objective function
		model.addMinimize(model.sum(model.scalProd(x, weights), model.scalProd(v, weights)));

		model.addEq(model.sum(y), k + 1); // constraint (1)

		model.addEq(model.sum(model.sum(x), model.sum(v)), k); // constraint (2)

		// constraint (3)
		IloLinearNumExpr oneExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				oneExitingByZero.addTerm(1, x[index]);
		model.addEq(oneExitingByZero, 1);
		// end constraint (3)

		// constraint (4)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneIncomingForNonRoot = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {
				if (edges[index].getEndpoint_2() == node)
					oneIncomingForNonRoot.addTerm(1, x[index]);
				else if (edges[index].getEndpoint_1() == node)
					oneIncomingForNonRoot.addTerm(1, v[index]);
			}
			model.addEq(oneIncomingForNonRoot, y[node]);
		}
		// end constraint (4)

//		// constraint (5)
//		for (int index = 0; index < numberOfEdges; index++) {
//			model.addLe(model.sum(x[index], v[index]), 1);
//		}
//		// end constraint (5)

		// constraint (6)
		for (int index = 0; index < numberOfEdges; index++) {
			int endPoint_1 = edges[index].getEndpoint_1();
			int endPoint_2 = edges[index].getEndpoint_2();

			model.addGe(model.sum(y[endPoint_1], y[endPoint_2]),
					model.sum(model.prod(2, x[index]), model.prod(2, v[index])));
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

			model.addLe(model.sum(u[endPoint_1], x[index]),
					model.sum(u[endPoint_2], model.prod(bigM, model.sum(1, model.prod(-1, x[index])))));

			model.addLe(model.sum(u[endPoint_2], v[index]),
					model.sum(u[endPoint_1], model.prod(bigM, model.sum(1, model.prod(-1, v[index])))));
		}
		// end constraint (8)

		model.addEq(u[0], 0); // constraint (9)

		model.addEq(y[0], 1); // constraint (10)
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
		weights = new int[numberOfEdges * 2 - (numberOfNodes - 1)]; // -(numberOfNodes-1) because we dont need (v,0)

		String currentRow;
		for (int rangeEdge = 0; rangeEdge < numberOfEdges; rangeEdge++) {
			currentRow = br.readLine();
			String parameters[] = currentRow.split(" ");
			int indexOfEdge = Integer.valueOf(parameters[0]);
			int endPoint_1 = Integer.valueOf(parameters[1]);
			int endPoint_2 = Integer.valueOf(parameters[2]);
			int weight = Integer.valueOf(parameters[3]);

			edges[indexOfEdge] = new Pair(endPoint_1, endPoint_2);

			weights[indexOfEdge] = weight;

			if (endPoint_1 != 0)
				weights[indexOfEdge + numberOfEdges - (numberOfNodes - 1)] = weight;

		}

	}
}