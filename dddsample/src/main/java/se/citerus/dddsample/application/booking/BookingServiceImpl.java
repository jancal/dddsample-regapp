package se.citerus.dddsample.application.booking;

import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;
import se.citerus.dddsample.application.CargoLockingService;
import se.citerus.dddsample.application.CargoLockingServiceInMem;
import se.citerus.dddsample.domain.model.cargo.*;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.location.LocationRepository;
import se.citerus.dddsample.domain.model.location.UnLocode;
import se.citerus.dddsample.domain.service.RoutingService;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class BookingServiceImpl implements BookingService {

  private final RoutingService routingService;
  private final CargoFactory cargoFactory;
  private final CargoRepository cargoRepository;
  private final LocationRepository locationRepository;
  private final CargoLockingService cargoLockingService;
  private final Log logger = LogFactory.getLog(getClass());

  public BookingServiceImpl(final RoutingService routingService,
                            final CargoFactory cargoFactory,
                            final CargoRepository cargoRepository,
                            final LocationRepository locationRepository) {
    this.routingService = routingService;
    this.cargoFactory = cargoFactory;
    this.cargoRepository = cargoRepository;
    this.locationRepository = locationRepository;
    this.cargoLockingService = new CargoLockingServiceInMem();
  }

  @Override
  @Transactional
  public TrackingId bookNewCargo(final UnLocode originUnLocode,
                                 final UnLocode destinationUnLocode,
                                 final Date arrivalDeadline) {
    final Cargo cargo = cargoFactory.newCargo(originUnLocode, destinationUnLocode, arrivalDeadline);
    cargoRepository.store(cargo);
    logger.info("Booked new cargo with tracking id " + cargo.trackingId().stringValue());

    return cargo.trackingId();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Itinerary> requestPossibleRoutesForCargo(final TrackingId trackingId) {
    final Cargo cargo = cargoRepository.find(trackingId);

    if (cargo == null) {
      return Collections.emptyList();
    }

    return routingService.fetchRoutesForSpecification(cargo.routeSpecification());
  }

  @Override
  @Transactional
  public void assignCargoToRoute(final Itinerary itinerary, final TrackingId trackingId) {
    cargoLockingService.assertLocked(trackingId);

    final Cargo cargo = cargoRepository.find(trackingId);
    Validate.notNull(cargo, "Can't assign itinerary to non-existing cargo " + trackingId);
    cargo.assignToRoute(itinerary);
    cargoRepository.store(cargo);

    logger.info("Assigned cargo " + trackingId + " to new route");

    cargoLockingService.unlock(trackingId);
  }

  @Override
  @Transactional
  public void changeDestination(final TrackingId trackingId, final UnLocode unLocode) {
    cargoLockingService.assertLocked(trackingId);

    final Cargo cargo = cargoRepository.find(trackingId);
    Validate.notNull(cargo, "Can't change destination of non-existing cargo " + trackingId);
    final Location newDestination = locationRepository.find(unLocode);

    final RouteSpecification routeSpecification = cargo.routeSpecification().withDestination(newDestination);
    cargo.specifyNewRoute(routeSpecification);

    cargoRepository.store(cargo);
    logger.info("Changed destination for cargo " + trackingId + " to " + routeSpecification.destination());

    cargoLockingService.unlock(trackingId);
  }

  @Override
  @Transactional(readOnly = true)
  public Cargo loadCargoForRouting(final TrackingId trackingId) {
    final Cargo cargo = cargoRepository.find(trackingId);
    if (cargo != null) {
      cargoLockingService.lock(trackingId);
    }
    return cargo;
  }

}