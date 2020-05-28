
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
public class MainVecchio {

	static int numberOfNodes;
	static int numberOfEdges;
	static Pair[] edges;

	static int[] weights;

	public static void main(String[] args) throws IOException, IloException {

		for (int i = 1; i <= 10; i++) {

			readInstance(i);

			for (int iter = 0; iter <= 1; iter++) {
				for (int a = 2; a <= 2; a++) {
					IloCplex model = new IloCplex();
					if (a == 0)
						createMtzModel(iter, model);
					else if (a == 1)
						createSCFModel(iter, model);
					else
						createMCFModel(iter, model);

					model.exportModel("old.lp");
					 long start = System.currentTimeMillis();
					// double startC = model.getCplexTime();
					if (model.solve()) {
					long elapsed = (System.currentTimeMillis() - start) / 1000;
//					double elapsedC = model.getCplexTime() - startC;
						System.out.print(model.getObjValue() + " "+elapsed);
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

	private static void createMCFModel(int iter, IloCplex model) throws IloException {
		int k = -1;
		if (iter == 1)
			k = numberOfNodes / 2;
		else
			k = numberOfNodes / 5;

		int bigM = k;
		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges);
		IloNumVar[] v = model.boolVarArray(numberOfEdges);

		IloNumVar[][] f = new IloNumVar[numberOfEdges][];
		for (int index = 0; index < numberOfEdges; index++)
			f[index] = model.numVarArray(numberOfNodes, 0, 1);

		IloNumVar[][] s = new IloNumVar[numberOfEdges][];
		for (int index = 0; index < numberOfEdges; index++)
			s[index] = model.numVarArray(numberOfNodes, 0, 1);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

		for (int index = 0; index < numberOfNodes; index++) {
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			v[index].setName("x_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");

			for (int i = 0; i < numberOfNodes; i++) {
				f[index][i].setName(
						"f_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "," + (i) + "}");
				s[index][i].setName(
						"f_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "," + (i) + "}");
			}

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
			else if (edges[index].getEndpoint_2() == 0)
				oneExitingByZero.addTerm(1, v[index]);

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
		for (int i = 0; i < numberOfNodes; i++) {
			for (int index = 0; index < numberOfNodes; index++) {
				// compute mantained flow for node index for commodity i
				IloLinearNumExpr mantainedFlowFromIndexCommmodityI = model.linearNumExpr();
				for (int j = 0; j < numberOfEdges; j++) {
					if (edges[j].getEndpoint_2() == index) {
						mantainedFlowFromIndexCommmodityI.addTerm(1, f[j][i]);
						mantainedFlowFromIndexCommmodityI.addTerm(-1, s[j][i]);
					} else if (edges[j].getEndpoint_1() == index) {
						mantainedFlowFromIndexCommmodityI.addTerm(-1, f[j][i]);
						mantainedFlowFromIndexCommmodityI.addTerm(1, s[j][i]);
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

		// end constraint (7)

		// constraint (8)
		for (int i = 0; i < numberOfNodes; i++) {
			for (int index = 0; index < numberOfEdges; index++) {
				model.addLe(f[index][i], model.prod(1, x[index]));
				model.addLe(s[index][i], model.prod(1, v[index]));

			}
		}
		// end constraint (8)

		 model.addEq(y[0], 1); // constraint (9)

	}

	private static void createSCFModel(int iter, IloCplex model) throws IloException {
		int k = -1;
		if (iter == 1)
			k = numberOfNodes / 2;
		else
			k = numberOfNodes / 5;

		int bigM = k;
		// creating the model
		model.setOut(null);
		model.setParam(IloCplex.Param.TimeLimit, 3600);

		// creating variables
		IloNumVar[] x = model.boolVarArray(numberOfEdges);
		IloNumVar[] v = model.boolVarArray(numberOfEdges);
		IloNumVar[] f = model.numVarArray(numberOfEdges, 0.0, k);
		IloNumVar[] s = model.numVarArray(numberOfEdges, 0.0, k);

		IloNumVar[] y = model.boolVarArray(numberOfNodes);

//		//provaaaaaaaaaaa (impongo zero agli archi entranti in 0)
//		for(int index = 0;index<numberOfEdges;index++)
//			if(edges[index].getEndpoint_1()==0) {
//				model.addEq(0, v[index]);
//				model.addEq(0, s[index]);
//			}
//			else if(edges[index].getEndpoint_2()==0) {
//				model.addEq(0, x[index]);
//				model.addEq(0, f[index]);
//			}
//			
//		
//		//endprovaaaaaaaa

		for (int index = 0; index < numberOfNodes; index++) {
			y[index].setName("y" + index);
		}

		for (int index = 0; index < numberOfEdges; index++) {
			x[index].setName("x_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			v[index].setName("x_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");

			f[index].setName("f_{" + edges[index].getEndpoint_1() + "," + edges[index].getEndpoint_2() + "}");
			s[index].setName("f_{" + edges[index].getEndpoint_2() + "," + edges[index].getEndpoint_1() + "}");

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
			else if (edges[index].getEndpoint_2() == 0)
				oneExitingByZero.addTerm(1, v[index]);

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
		IloLinearNumExpr kExitingByZero = model.linearNumExpr();
		for (int index = 0; index < numberOfEdges; index++)
			if (edges[index].getEndpoint_1() == 0)
				kExitingByZero.addTerm(1, f[index]);
		model.addEq(kExitingByZero, k);
		// end constraint (7)

		// constraint (8)
		for (int node = 1; node < numberOfNodes; node++) {
			IloLinearNumExpr oneFlowMantained = model.linearNumExpr();
			for (int index = 0; index < numberOfEdges; index++) {
				if (edges[index].getEndpoint_2() == node) {
					oneFlowMantained.addTerm(1, f[index]);
					oneFlowMantained.addTerm(-1, s[index]);
				} else if (edges[index].getEndpoint_1() == node) {
					oneFlowMantained.addTerm(1, s[index]);
					oneFlowMantained.addTerm(-1, f[index]);
				}
			}
			model.addEq(oneFlowMantained, y[node]);
		}
		// end constraint (8)

		for (int index = 0; index < numberOfEdges; index++) {
			model.addLe(f[index], model.prod(k, x[index]));
			model.addLe(s[index], model.prod(k, v[index]));

		}
		
		model.addEq(y[0], 1); // constraint (9)

	}

	private static void createMtzModel(int iter, IloCplex model) throws IloException {
		int k = -1;
		if (iter == 1)
			k = numberOfNodes / 2;
		else
			k = numberOfNodes / 5;

		int bigM = k;
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


		BufferedReader br = new BufferedReader(new FileReader(file));

		numberOfNodes = Integer.valueOf(br.readLine());
		numberOfEdges = Integer.valueOf(br.readLine());
		edges = new Pair[numberOfEdges];
		weights = new int[numberOfEdges];

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
		}

	}
}
