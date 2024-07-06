package interpreter

import (
	"fmt"
	"iter"
	"log"
	"math"
	"net/http"
	"time"
)

const MAX_LAYERS = 3

type TestConfigurationDeployments struct {
	adminUrl    string
	deployments []string
}

type TestConfiguration struct {
	ingress        string
	seed           string
	keys           int
	tests          int
	maxProgramSize int
	register       *TestConfigurationDeployments // auto register the following endpoints
	bootstrap      *bool
	crashInterval  *int
}

type TestStatus string

const (
	TestStatus_NOT_STARTED TestStatus = "NOT_STARTED"
	TestStatus_RUNNING     TestStatus = "RUNNING"
	TestStatus_VALIDATING  TestStatus = "VALIDATING"
	TestStatus_FINISHED    TestStatus = "FINISHED"
	TestStatus_FAILED      TestStatus = "FAILED"
)

type StateTracker struct {
	numLayers, numInterpreters int
	states                     [][]int
}

func NewStateTracker(numLayers int, numInterpreters int) *StateTracker {
	s := &StateTracker{
		numLayers:       numLayers,
		numInterpreters: numInterpreters,
		states:          make([][]int, numLayers),
	}
	for i := 0; i < numLayers; i++ {
		s.states[i] = make([]int, numInterpreters)
	}
	return s
}

func (s *StateTracker) update(layer int, id int, program Program) error {
	if layer >= s.numLayers {
		return fmt.Errorf("InterpreterDriver bug.")
	}
	for _, command := range program.Commands {
		switch command.GetKind() {
		case INCREMENT_STATE_COUNTER, INCREMENT_STATE_COUNTER_INDIRECTLY, INCREMENT_VIA_DELAYED_CALL, INCREMENT_STATE_COUNTER_VIA_AWAKEABLE:
			s.states[layer][id] += 1
		case CALL_NEXT_LAYER_OBJECT:
			command := command.(CallObject)
			{
				s.update(layer+1, command.Key, command.Program)
			}
		}
	}
	return nil
}

func (s *StateTracker) getLayer(layer uint) []int {
	return s.states[layer]
}

type Test struct {
	conf         *TestConfiguration
	random       *Random
	stateTracker *StateTracker
	status       TestStatus
	containers   *TestEnvironment
}

func NewTest(conf *TestConfiguration) *Test {
	return &Test{
		conf:         conf,
		random:       NewRandom(conf.seed),
		stateTracker: NewStateTracker(MAX_LAYERS, conf.keys),
		status:       TestStatus_NOT_STARTED,
		containers:   nil,
	}
}

func (t *Test) TestStatus() TestStatus {
	return t.status
}

func (t *Test) Go() TestStatus {
	return TestStatus_NOT_STARTED
}

type Foo struct {
	id      int
	program Program
}

func (t *Test) Generate() iter.Seq[Foo] {
	testCaseCount := t.conf.tests
	gen := &ProgramGenerator{
		rand:                t.random,
		interpreterCount:    t.conf.keys,
		maximumCommandCount: t.conf.maxProgramSize,
	}

	keys := t.conf.keys
	rnd := t.random
	return func(yield func(Foo) bool) {
		for i := 0; i < testCaseCount; i++ {
			program := gen.generateProgram(0)
			id := int(math.Floor(rnd.Random() * float64(keys)))
			if !yield(Foo{id: id, program: program}) {
				return
			}
		}
	}
}

func ingressReady(ingressUrl string) {
	for {
		resp, err := http.Get(fmt.Sprintf("%s/restate/health", ingressUrl))
		if err == nil && resp.StatusCode == 200 {
			break
		}
		log.Printf("Waiting for %s to be healthy...\n", ingressUrl)
		time.Sleep(2 * time.Second)
	}
	log.Println("Ingress is ready.")
}

 func registerEndpoints(adminUrl string, deployments string[]) error {
    if (adminUrl == "") {
      return fmt.Errorf("Missing adminUrl");
    }
    if (len(deployments) == 0) {
      return fmt.Errorf("Missing register.deployments (array of uri string)");
    }
    for {
      try {
        const rc = await fetch(`${adminUrl}/health`);
        if (rc.ok) {
          break;
        }
      } catch (e) {
        // suppress
      }
      console.log(`Waiting for ${adminUrl} to be healthy...`);
      await sleep(2000);
    }
    console.log("Admin is ready.");
    for (const uri of deployments) {
      const res = await fetch(`${adminUrl}/deployments`, {
        method: "POST",
        body: JSON.stringify({
          uri,
        }),
        headers: {
          "Content-Type": "application/json",
        },
      });
      if (!res.ok) {
        throw new Error(
          `unable to register ${uri} because: ${await res.text()}`
        );
      }
    }
    console.log("Registered deployments");
  }

//   async go(): Promise<TestStatus> {
//     try {
//       return await this.startTesting();
//     } catch (e) {
//       console.error(e);
//       this.status = TestStatus.FAILED;
//       throw e;
//     } finally {
//       await this.cleanup();
//     }
//   }

//   async startTesting(): Promise<TestStatus> {
//     console.log(this.conf);
//     this.status = TestStatus.RUNNING;

//     if (this.conf.bootstrap) {
//       this.containers = await setupContainers();
//       console.log(this.containers);
//     }
//     const ingressUrl = this.containers?.ingressUrl ?? this.conf.ingress;
//     const adminUrl = this.containers?.adminUrl ?? this.conf.register?.adminUrl;
//     const deployments =
//       this.containers?.services ?? this.conf.register?.deployments;

//     let ingress = restate.connect({ url: ingressUrl });

//     if (deployments) {
//       await this.registerEndpoints(adminUrl, deployments);
//     }
//     await this.ingressReady(ingressUrl);

//     console.log("Generating ...");

//     const killRestate = async () => {
//       const container = this.containers?.containers.restateContainer;
//       if (!container) {
//         return;
//       }
//       const interval = this.conf.crashInterval;
//       if (!interval) {
//         return;
//       }
//       for (;;) {
//         await sleep(interval);
//         if (
//           this.status == TestStatus.FAILED ||
//           this.status == TestStatus.FINISHED
//         ) {
//           break;
//         }
//         console.log("Killing restate");
//         await container.restart({ timeout: 1 });
//         const newIngressUrl = `http://${container.getHost()}:${container.getMappedPort(
//           8080
//         )}`;
//         const newAdminUrl = `http://${container.getHost()}:${container.getMappedPort(
//           9070
//         )}`;
//         ingress = restate.connect({ url: newIngressUrl });
//         console.log(
//           `Restate is back:\ningress: ${newIngressUrl}\nadmin:  ${newAdminUrl}`
//         );
//       }
//     };

//     killRestate().catch(console.error);

//     let idempotencyKey = 1;
//     for (const b of batch(this.generate(), 32)) {
//       const promises = b.map(({ id, program }) => {
//         idempotencyKey += 1;
//         const key = `${idempotencyKey}`;
//         return retry(() => {
//           const client = ingress.objectSendClient(InterpreterL0, `${id}`);
//           return client.interpret(
//             program,
//             restate.SendOpts.from({
//               idempotencyKey: key,
//             })
//           );
//         });
//       });

//       b.forEach(({ id, program }) => this.stateTracker.update(0, id, program));
//       try {
//         await Promise.all(promises);
//       } catch (e) {
//         console.error(e);
//         throw e;
//       }
//     }

//     this.status = TestStatus.VALIDATING;
//     console.log("Done generating");

//     for (const layerId of [0, 1, 2]) {
//       try {
//         while (!(await this.verifyLayer(ingress, layerId))) {
//           await sleep(10 * 1000);
//         }
//       } catch (e) {
//         console.error(e);
//         console.error(`Failed to validate layer ${layerId}, retrying...`);
//         await sleep(1000);
//       }
//       console.log(`Done validating layer ${layerId}`);
//     }

//     console.log("Done.");
//     this.status = TestStatus.FINISHED;
//     return TestStatus.FINISHED;
//   }

//   private async cleanup() {
//     if (this.containers) {
//       console.log("Cleaning up containers");
//       await tearDown(this.containers);
//     }
//   }

//   async verifyLayer(
//     ingress: restate.Ingress,
//     layerId: number
//   ): Promise<boolean> {
//     console.log(`Trying to verify layer ${layerId}`);

//     const layer = this.stateTracker.getLayer(layerId);
//     const interpreterLn = createInterpreterObject(layerId);

//     for (const layerChunk of batch(iterate(layer), 256)) {
//       const futures = layerChunk.map(async ({ expected, id }) => {
//         const actual = await retry(
//           async () =>
//             await ingress.objectClient(interpreterLn, `${id}`).counter()
//         );
//         return { expected, actual, id };
//       });
//       await Promise.all(futures);
//       for await (const { expected, actual, id } of futures) {
//         if (expected !== actual) {
//           console.log(
//             `Found a mismatch at layer ${layerId} interpreter ${id}. Expected ${expected} but got ${actual}. This is expected, this interpreter might still have some backlog to process, and eventually it will catchup with the desired value. Will retry in few seconds.`
//           );
//           return false;
//         }
//       }
//     }
//     return true;
//   }
// }

// const InterpreterL0 = interpreterObjectForLayer(0);
